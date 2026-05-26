package com.stayops.reservation.application.service

import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentCancelReason
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.service.PaymentConfirmResult
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.payment.domain.service.PaymentGatewayException
import com.stayops.payment.domain.service.PaymentInquiryResult
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.exception.ConflictException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class ReservationPaymentOutboxApplication(
    private val outboxRepository: PaymentOutboxRepository,
    private val paymentRepository: PaymentRepository,
    private val reservationRepository: ReservationRepository,
    private val inventoryReservationPort: InventoryReservationPort,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun processPendingMessages(workerId: String = "payment-outbox-worker") {
        val now = clock.instant()
        outboxRepository.findReadyForProcessing(now).forEach { message ->
            val processing = try {
                outboxRepository.save(message.startProcessing(workerId, now.plusSeconds(60), now))
            } catch (e: ConflictException) {
                log.info("PaymentOutbox lease 획득 실패, 다음 폴링에서 재시도: outboxId={}", message.id)
                return@forEach
            }

            try {
                process(processing)
            } catch (e: Exception) {
                log.error("PaymentOutbox 처리 실패: outboxId={}", processing.id, e)
                outboxRepository.save(processing.fail(e.message ?: e.javaClass.simpleName, clock.instant()))
            }
        }
    }

    private fun process(message: PaymentOutboxMessage) {
        when (message.type) {
            PaymentOutboxType.CONFIRM_PAYMENT -> confirmPayment(message)
            PaymentOutboxType.CANCEL_PAYMENT -> cancelPayment(message)
        }
    }

    private fun confirmPayment(message: PaymentOutboxMessage) {
        val now = clock.instant()
        val payment = paymentRepository.findById(message.paymentId)
        val reservation = reservationRepository.findById(message.reservationId)

        if (payment == null || reservation == null) {
            outboxRepository.save(message.skip("결제 또는 예약을 찾을 수 없습니다", now))
            return
        }

        if (reservation.status == ReservationStatus.CANCELLED || payment.status == PaymentStatus.FAILED) {
            val currentPayment = if (
                reservation.status == ReservationStatus.CANCELLED &&
                (payment.status == PaymentStatus.PENDING || payment.status == PaymentStatus.CONFIRM_REQUESTED)
            ) {
                paymentRepository.save(payment.fail("예약 취소로 결제 승인 요청을 건너뜁니다"))
            } else {
                payment
            }
            log.info(
                "결제 승인 Outbox 건너뜀: outboxId={}, reservationStatus={}, paymentStatus={}",
                message.id,
                reservation.status,
                currentPayment.status
            )
            outboxRepository.save(message.skip("예약 또는 결제가 이미 종료 상태입니다", now))
            return
        }

        if (payment.status == PaymentStatus.APPROVED) {
            if (reservation.status == ReservationStatus.PENDING) {
                reservationRepository.save(reservation.confirm())
            }
            outboxRepository.save(message.complete(now))
            return
        }

        try {
            val confirmResult = paymentGateway.confirm(
                paymentKey = message.paymentKey,
                orderId = message.orderId,
                amount = message.amount.amount,
                idempotencyKey = message.idempotencyKey
            )
            approveAndConfirm(message, payment, reservation, confirmResult, now)
        } catch (e: PaymentGatewayException.AlreadyProcessed) {
            recoverByInquiry(message, payment, reservation, e, failPaymentWhenNotDone = true)
        } catch (e: PaymentGatewayException.ProviderError) {
            recoverByInquiry(message, payment, reservation, e, failPaymentWhenNotDone = false)
        } catch (e: PaymentGatewayException.PaymentDeclined) {
            paymentRepository.save(payment.fail(e.reason))
            outboxRepository.save(message.complete(clock.instant()))
        } catch (e: PaymentGatewayException.InvalidRequest) {
            paymentRepository.save(payment.fail(e.reason))
            outboxRepository.save(message.skip("PG 요청이 유효하지 않습니다: ${e.reason}", clock.instant()))
        } catch (e: PaymentGatewayException.UnknownError) {
            outboxRepository.save(message.fail(e.message ?: "알 수 없는 결제 오류", clock.instant()))
        }
    }

    private fun recoverByInquiry(
        message: PaymentOutboxMessage,
        payment: Payment,
        reservation: Reservation,
        cause: PaymentGatewayException,
        failPaymentWhenNotDone: Boolean
    ) {
        val inquiry = try {
            paymentGateway.inquire(message.paymentKey)
        } catch (e: PaymentGatewayException) {
            outboxRepository.save(message.fail(e.message ?: cause.message ?: "PG 상태 조회 실패", clock.instant()))
            return
        }

        if (isDoneForMessage(inquiry, message)) {
            approveAndConfirm(
                message = message,
                payment = payment,
                reservation = reservation,
                confirmResult = PaymentConfirmResult(
                    paymentKey = inquiry.paymentKey,
                    orderId = inquiry.orderId,
                    method = "unknown",
                    approvedAt = clock.instant(),
                    totalAmount = inquiry.totalAmount,
                    receiptUrl = null,
                    cardNumber = null,
                    cardCompany = null
                ),
                now = clock.instant()
            )
            return
        }

        if (failPaymentWhenNotDone) {
            paymentRepository.save(payment.fail("외부 결제 상태가 DONE이 아닙니다: ${inquiry.status}"))
            outboxRepository.save(message.complete(clock.instant()))
            return
        }

        outboxRepository.save(message.fail("외부 결제 상태가 아직 DONE이 아닙니다: ${inquiry.status}", clock.instant()))
    }

    private fun approveAndConfirm(
        message: PaymentOutboxMessage,
        payment: Payment,
        reservation: Reservation,
        confirmResult: PaymentConfirmResult,
        now: Instant
    ) {
        if (confirmResult.orderId != message.orderId || confirmResult.totalAmount.compareTo(message.amount.amount) != 0) {
            paymentRepository.save(payment.fail("PG 승인 결과가 요청 값과 일치하지 않습니다"))
            outboxRepository.save(message.skip("PG 승인 결과 불일치", now))
            return
        }

        val approvedPayment = if (payment.status == PaymentStatus.APPROVED) {
            payment
        } else {
            paymentRepository.save(
                payment.approve(
                    paymentKey = confirmResult.paymentKey,
                    method = confirmResult.method ?: "unknown",
                    approvedAt = confirmResult.approvedAt ?: now
                )
            )
        }

        val reservedDates = mutableListOf<LocalDate>()
        try {
            reservation.dateRange.allDates().forEach { date ->
                inventoryReservationPort.reserve(reservation.propertyId, reservation.roomTypeId, date)
                reservedDates += date
            }
        } catch (e: IllegalArgumentException) {
            releaseReservedInventory(reservation, reservedDates)
            requestPaymentCancelForInventoryUnavailable(message, approvedPayment, reservation, now)
            return
        } catch (e: ConflictException) {
            releaseReservedInventory(reservation, reservedDates)
            outboxRepository.save(message.fail(e.message ?: "재고 변경 충돌이 발생했습니다", clock.instant()))
            return
        }

        if (reservation.status == ReservationStatus.PENDING) {
            reservationRepository.save(reservation.confirm())
        }
        outboxRepository.save(message.complete(clock.instant()))
    }

    private fun requestPaymentCancelForInventoryUnavailable(
        message: PaymentOutboxMessage,
        payment: Payment,
        reservation: Reservation,
        now: Instant
    ) {
        val cancelRequestedPayment = paymentRepository.save(payment.requestCancel())

        if (reservation.status == ReservationStatus.PENDING) {
            reservationRepository.save(reservation.cancelPending())
        }

        val existingCancelOutbox = outboxRepository.findByPaymentIdAndType(
            cancelRequestedPayment.id,
            PaymentOutboxType.CANCEL_PAYMENT
        )
        if (existingCancelOutbox == null) {
            outboxRepository.save(
                PaymentOutboxMessage.createCancel(
                    id = idGenerator.generate(),
                    paymentId = cancelRequestedPayment.id,
                    reservationId = reservation.id,
                    memberId = reservation.memberId ?: message.memberId,
                    paymentKey = cancelRequestedPayment.paymentKey ?: message.paymentKey,
                    orderId = cancelRequestedPayment.orderId,
                    amount = cancelRequestedPayment.amount,
                    cancelReason = PaymentCancelReason.INVENTORY_UNAVAILABLE.message,
                    now = now
                )
            )
        }

        outboxRepository.save(message.complete(clock.instant()))
    }

    private fun releaseReservedInventory(reservation: Reservation, reservedDates: List<LocalDate>) {
        reservedDates.forEach { date ->
            try {
                inventoryReservationPort.release(reservation.propertyId, reservation.roomTypeId, date)
            } catch (e: Exception) {
                log.error("결제 승인 중 재고 보상 해제 실패: reservationId={}, date={}", reservation.id, date, e)
            }
        }
    }

    private fun isDoneForMessage(inquiry: PaymentInquiryResult, message: PaymentOutboxMessage): Boolean =
        inquiry.status == "DONE" &&
            inquiry.orderId == message.orderId &&
            inquiry.totalAmount.compareTo(message.amount.amount) == 0

    private fun cancelPayment(message: PaymentOutboxMessage) {
        val now = clock.instant()
        val payment = paymentRepository.findById(message.paymentId)
        val reservation = reservationRepository.findById(message.reservationId)

        if (payment == null || reservation == null) {
            outboxRepository.save(message.skip("결제 또는 예약을 찾을 수 없습니다", now))
            return
        }

        if (payment.status == PaymentStatus.CANCELLED) {
            outboxRepository.save(message.complete(now))
            return
        }

        if (payment.status == PaymentStatus.CANCEL_FAILED || payment.status == PaymentStatus.FAILED) {
            outboxRepository.save(message.skip("취소할 수 없는 결제 상태입니다: ${payment.status}", now))
            return
        }

        try {
            paymentGateway.cancel(
                paymentKey = message.paymentKey,
                cancelReason = message.cancelReason ?: PaymentCancelReason.CUSTOMER_REQUEST.message,
                idempotencyKey = message.idempotencyKey
            )
            paymentRepository.save(payment.cancel())
            outboxRepository.save(message.complete(clock.instant()))
        } catch (e: PaymentGatewayException.AlreadyProcessed) {
            paymentRepository.save(payment.cancel())
            outboxRepository.save(message.complete(clock.instant()))
        } catch (e: PaymentGatewayException.ProviderError) {
            retryOrFailCancel(message, payment, e.message ?: "PG사 취소 처리 오류")
        } catch (e: PaymentGatewayException.UnknownError) {
            retryOrFailCancel(message, payment, e.message ?: "알 수 없는 결제 취소 오류")
        } catch (e: PaymentGatewayException.PaymentDeclined) {
            paymentRepository.save(payment.failCancel(e.reason))
            outboxRepository.save(message.skip("PG 취소 거절: ${e.reason}", clock.instant()))
        } catch (e: PaymentGatewayException.InvalidRequest) {
            paymentRepository.save(payment.failCancel(e.reason))
            outboxRepository.save(message.skip("PG 취소 요청이 유효하지 않습니다: ${e.reason}", clock.instant()))
        }
    }

    private fun retryOrFailCancel(message: PaymentOutboxMessage, payment: Payment, errorMessage: String) {
        val failedMessage = message.fail(errorMessage, clock.instant())
        if (failedMessage.status == PaymentOutboxStatus.FAILED) {
            paymentRepository.save(payment.failCancel(errorMessage))
        }
        outboxRepository.save(failedMessage)
    }
}
