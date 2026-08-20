package com.stayops.reservation.application.service

import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.application.required.ReservationPaymentSnapshot
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ForbiddenException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock

@Service
class CustomerReservationIntentPaymentApplication(
    private val reservationIntentRepository: ReservationIntentRepository,
    private val reservationPaymentService: ReservationPaymentService,
    private val clock: Clock
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun getPaymentStatus(
        memberId: String,
        reservationIntentId: String
    ): CustomerReservationIntentResult {
        val intent = reservationIntentRepository.findById(reservationIntentId)
            ?: throw NotFoundException("RESERVATION_INTENT_NOT_FOUND", "예약 intent를 찾을 수 없습니다: $reservationIntentId")
        if (intent.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약 intent만 조회할 수 있습니다")
        }

        val payment = reservationPaymentService.findByReservationIntentId(reservationIntentId)
            ?: throw NotFoundException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: $reservationIntentId")

        return CustomerReservationIntentResult(intent, payment)
    }

    @Transactional
    fun confirmPayment(
        memberId: String,
        reservationIntentId: String,
        paymentKey: String,
        orderId: String,
        amount: BigDecimal
    ): CustomerReservationIntentResult {
        val intent = reservationIntentRepository.findById(reservationIntentId)
            ?: throw NotFoundException("RESERVATION_INTENT_NOT_FOUND", "예약 intent를 찾을 수 없습니다: $reservationIntentId")
        if (intent.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약 intent만 결제할 수 있습니다")
        }

        val payment = reservationPaymentService.findByReservationIntentId(reservationIntentId)
            ?: throw NotFoundException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: $reservationIntentId")

        // These three branches all answer the caller with 202, so without a log line the
        // response alone cannot tell an accepted request from a no-op
        if (intent.status == ReservationIntentStatus.RESERVED || payment.status == ReservationPaymentStatus.APPROVED) {
            logConfirmIgnored(intent, payment, "already_finalized")
            return CustomerReservationIntentResult(intent, payment)
        }
        if (
            intent.status == ReservationIntentStatus.PAYMENT_FAILED ||
            intent.status == ReservationIntentStatus.EXPIRED ||
            intent.status == ReservationIntentStatus.CANCELLED ||
            payment.status == ReservationPaymentStatus.FAILED ||
            payment.status == ReservationPaymentStatus.CANCELLED
        ) {
            logConfirmIgnored(intent, payment, "already_terminated")
            return CustomerReservationIntentResult(intent, payment)
        }
        if (clock.instant().isAfter(intent.expiresAt)) {
            log.warn(
                "[결제 승인 요청 거부] event=payment_confirm_rejected reservationIntentId={} paymentId={} " +
                    "orderId={} intentStatus={} paymentStatus={} expiresAt={} reason=intent_expired",
                intent.id,
                payment.id,
                payment.orderId,
                intent.status,
                payment.status,
                intent.expiresAt
            )
            throw BusinessException("RESERVATION_INTENT_EXPIRED", "결제 가능 시간이 만료되었습니다")
        }

        val requestedIntent = if (intent.status == ReservationIntentStatus.CONFIRM_REQUESTED) {
            intent
        } else {
            reservationIntentRepository.save(intent.requestPaymentConfirmation(clock.instant()))
        }
        val requestedPayment = reservationPaymentService.requestConfirmForReservationIntent(
            reservationIntentId = reservationIntentId,
            memberId = memberId,
            paymentKey = paymentKey,
            orderId = orderId,
            amount = amount
        )

        log.info(
            "[결제 승인 요청 수락] event=payment_confirm_accepted reservationIntentId={} paymentId={} " +
                "orderId={} amount={} intentStatus={} paymentStatus={}",
            reservationIntentId,
            requestedPayment.id,
            requestedPayment.orderId,
            requestedPayment.amount.amount,
            requestedIntent.status,
            requestedPayment.status
        )
        return CustomerReservationIntentResult(requestedIntent, requestedPayment)
    }

    private fun logConfirmIgnored(
        intent: ReservationIntent,
        payment: ReservationPaymentSnapshot,
        reason: String
    ) {
        log.info(
            "[결제 승인 요청 무시] event=payment_confirm_ignored reservationIntentId={} paymentId={} " +
                "orderId={} intentStatus={} paymentStatus={} reason={}",
            intent.id,
            payment.id,
            payment.orderId,
            intent.status,
            payment.status,
            reason
        )
    }
}
