package com.stayops.reservation.application.service

import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.reservation.application.port.ReservationPaymentPort
import com.stayops.reservation.application.port.ReservationPaymentSnapshot
import com.stayops.reservation.application.port.ReservationPaymentStatus
import com.stayops.reservation.domain.event.ReservationCancelled
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.exception.ForbiddenException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerReservationCancellationApplication(
    private val reservationRepository: ReservationRepository,
    private val reservationPaymentPort: ReservationPaymentPort,
    private val inventoryReservationPort: InventoryReservationPort,
    private val eventPublisher: ApplicationEventPublisher
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun cancelReservation(memberId: String, reservationId: String): CustomerReservationResult {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        if (reservation.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약만 취소할 수 있습니다")
        }
        val payment = reservationPaymentPort.findByReservationId(reservationId)
            ?: throw NotFoundException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: $reservationId")

        val cancelledReservation: Reservation
        val cancelledPayment: ReservationPaymentSnapshot
        val shouldRequestRefund = payment.status == ReservationPaymentStatus.APPROVED ||
            payment.status == ReservationPaymentStatus.CANCEL_REQUESTED ||
            payment.status == ReservationPaymentStatus.CANCEL_FAILED

        if (reservation.status == ReservationStatus.PENDING && !shouldRequestRefund) {
            cancelledReservation = reservationRepository.save(reservation.cancelPending())
            cancelledPayment = reservationPaymentPort.cancelPendingByCustomerRequest(reservation.id)
        } else {
            cancelledReservation = reservationRepository.save(
                if (reservation.status == ReservationStatus.PENDING) reservation.cancelPending() else reservation.cancel()
            )
            cancelledPayment = reservationPaymentPort.requestCancelByCustomerRequest(
                reservationId = reservation.id,
                memberId = memberId
            )

            reservation.dateRange.allDates().forEach { date ->
                inventoryReservationPort.release(reservation.propertyId, reservation.roomTypeId, date)
            }
            eventPublisher.publishEvent(
                ReservationCancelled(
                    reservationId = cancelledReservation.id,
                    propertyId = reservation.propertyId,
                    roomTypeId = reservation.roomTypeId,
                    dateRange = reservation.dateRange
                )
            )
        }

        log.info("예약 취소: reservationId={}, 이전상태={}", reservationId, reservation.status)
        return CustomerReservationResult(cancelledReservation, cancelledPayment)
    }
}
