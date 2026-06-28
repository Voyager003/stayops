package com.stayops.reservation.application.service

import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.application.required.ReservationPaymentStatus
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

        if (intent.status == ReservationIntentStatus.RESERVED || payment.status == ReservationPaymentStatus.APPROVED) {
            return CustomerReservationIntentResult(intent, payment)
        }
        if (clock.instant().isAfter(intent.expiresAt)) {
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
            "예약 intent 결제 승인 요청 접수: reservationIntentId={}, paymentId={}",
            reservationIntentId,
            requestedPayment.id
        )
        return CustomerReservationIntentResult(requestedIntent, requestedPayment)
    }
}
