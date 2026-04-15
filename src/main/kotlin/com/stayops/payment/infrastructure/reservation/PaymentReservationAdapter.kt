package com.stayops.payment.infrastructure.reservation

import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentCancelReason
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.reservation.application.port.ReservationPaymentPort
import com.stayops.reservation.application.port.ReservationPaymentSnapshot
import com.stayops.reservation.application.port.ReservationPaymentStatus
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock

@Component
class PaymentReservationAdapter(
    private val paymentRepository: PaymentRepository,
    private val paymentOutboxRepository: PaymentOutboxRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) : ReservationPaymentPort {

    override fun createPendingPayment(
        reservationId: String,
        memberId: String,
        amount: Money
    ): ReservationPaymentSnapshot =
        paymentRepository.save(
            Payment.create(
                id = idGenerator.generate(),
                reservationId = reservationId,
                memberId = memberId,
                amount = amount
            )
        ).toSnapshot()

    override fun findByReservationId(reservationId: String): ReservationPaymentSnapshot? =
        paymentRepository.findByReservationId(reservationId)?.toSnapshot()

    override fun findByMemberId(memberId: String): List<ReservationPaymentSnapshot> =
        paymentRepository.findByMemberId(memberId).map { it.toSnapshot() }

    override fun requestConfirm(
        reservationId: String,
        memberId: String,
        paymentKey: String,
        orderId: String,
        amount: BigDecimal
    ): ReservationPaymentSnapshot {
        val payment = findPaymentByReservationId(reservationId)
        validatePaymentOwner(payment, memberId)
        validateConfirmRequest(payment, orderId, amount)

        val requestedPayment = payment.requestConfirm(paymentKey)
        val savedPayment = if (requestedPayment == payment) {
            payment
        } else {
            paymentRepository.save(requestedPayment)
        }

        val existingOutbox = paymentOutboxRepository.findByPaymentIdAndType(
            savedPayment.id,
            PaymentOutboxType.CONFIRM_PAYMENT
        )
        if (existingOutbox == null) {
            paymentOutboxRepository.save(
                PaymentOutboxMessage.createConfirm(
                    id = idGenerator.generate(),
                    paymentId = savedPayment.id,
                    reservationId = reservationId,
                    memberId = memberId,
                    paymentKey = paymentKey,
                    orderId = savedPayment.orderId,
                    amount = savedPayment.amount,
                    now = clock.instant()
                )
            )
        }

        return savedPayment.toSnapshot()
    }

    override fun cancelPendingByCustomerRequest(reservationId: String): ReservationPaymentSnapshot {
        val payment = findPaymentByReservationId(reservationId)
        return paymentRepository.save(payment.fail(PaymentCancelReason.CUSTOMER_REQUEST.message)).toSnapshot()
    }

    override fun requestCancelByCustomerRequest(
        reservationId: String,
        memberId: String
    ): ReservationPaymentSnapshot {
        val payment = findPaymentByReservationId(reservationId)
        validatePaymentOwner(payment, memberId)

        val requestedPayment = payment.requestCancel()
        val savedPayment = if (requestedPayment == payment) {
            payment
        } else {
            paymentRepository.save(requestedPayment)
        }

        val existingOutbox = paymentOutboxRepository.findByPaymentIdAndType(
            savedPayment.id,
            PaymentOutboxType.CANCEL_PAYMENT
        )
        if (existingOutbox == null) {
            paymentOutboxRepository.save(
                PaymentOutboxMessage.createCancel(
                    id = idGenerator.generate(),
                    paymentId = savedPayment.id,
                    reservationId = reservationId,
                    memberId = memberId,
                    paymentKey = savedPayment.paymentKey!!,
                    orderId = savedPayment.orderId,
                    amount = savedPayment.amount,
                    cancelReason = PaymentCancelReason.CUSTOMER_REQUEST.message,
                    now = clock.instant()
                )
            )
        }

        return savedPayment.toSnapshot()
    }

    private fun findPaymentByReservationId(reservationId: String): Payment =
        paymentRepository.findByReservationId(reservationId)
            ?: throw NotFoundException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: $reservationId")

    private fun validatePaymentOwner(payment: Payment, memberId: String) {
        if (payment.memberId != memberId) {
            throw BusinessException("PAYMENT_OWNER_MISMATCH", "예약자와 결제 소유자가 일치하지 않습니다")
        }
    }

    private fun validateConfirmRequest(payment: Payment, orderId: String, amount: BigDecimal) {
        if (payment.orderId != orderId) {
            throw BusinessException(
                "ORDER_ID_MISMATCH",
                "주문 ID가 일치하지 않습니다. 예상: ${payment.orderId}, 요청: $orderId"
            )
        }

        if (payment.amount.amount.compareTo(amount) != 0) {
            throw BusinessException(
                "AMOUNT_MISMATCH",
                "결제 금액이 일치하지 않습니다. 예상: ${payment.amount.amount}, 요청: $amount"
            )
        }

        if (payment.status == PaymentStatus.FAILED || payment.status == PaymentStatus.CANCELLED) {
            throw BusinessException("PAYMENT_NOT_CONFIRMABLE", "승인 요청할 수 없는 결제 상태입니다: ${payment.status}")
        }
    }

    private fun Payment.toSnapshot(): ReservationPaymentSnapshot =
        ReservationPaymentSnapshot(
            id = id,
            reservationId = reservationId,
            memberId = memberId,
            orderId = orderId,
            amount = amount,
            status = status.toReservationPaymentStatus(),
            paymentKey = paymentKey,
            failReason = failReason
        )

    private fun PaymentStatus.toReservationPaymentStatus(): ReservationPaymentStatus =
        when (this) {
            PaymentStatus.PENDING -> ReservationPaymentStatus.PENDING
            PaymentStatus.CONFIRM_REQUESTED -> ReservationPaymentStatus.CONFIRM_REQUESTED
            PaymentStatus.APPROVED -> ReservationPaymentStatus.APPROVED
            PaymentStatus.FAILED -> ReservationPaymentStatus.FAILED
            PaymentStatus.CANCEL_REQUESTED -> ReservationPaymentStatus.CANCEL_REQUESTED
            PaymentStatus.CANCELLED -> ReservationPaymentStatus.CANCELLED
            PaymentStatus.CANCEL_FAILED -> ReservationPaymentStatus.CANCEL_FAILED
        }
}
