package com.stayops.reservation.application.service

import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ForbiddenException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock

@Service
class CustomerReservationPaymentApplication(
    private val reservationRepository: ReservationRepository,
    private val reservationPaymentPort: ReservationPaymentService,
    private val clock: Clock
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun confirmPayment(
        memberId: String,
        reservationId: String,
        paymentKey: String,
        orderId: String,
        amount: BigDecimal
    ): CustomerReservationResult {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        if (reservation.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약만 결제할 수 있습니다")
        }

        val payment = reservationPaymentPort.findByReservationId(reservationId)
            ?: throw NotFoundException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: $reservationId")

        if (reservation.status == ReservationStatus.CONFIRMED || payment.status == ReservationPaymentStatus.APPROVED) {
            return CustomerReservationResult(reservation, payment)
        }

        if (reservation.expiresAt != null && clock.instant().isAfter(reservation.expiresAt)) {
            throw BusinessException("RESERVATION_EXPIRED", "결제 가능 시간이 만료되었습니다")
        }

        val savedPayment = reservationPaymentPort.requestConfirm(
            reservationId = reservation.id,
            memberId = memberId,
            paymentKey = paymentKey,
            orderId = orderId,
            amount = amount
        )

        log.info("결제 승인 요청 접수: reservationId={}, paymentId={}", reservationId, savedPayment.id)
        return CustomerReservationResult(reservation, savedPayment)
    }
}
