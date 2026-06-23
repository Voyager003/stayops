package com.stayops.reservation.application.service

import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationStatus
import org.springframework.stereotype.Component

@Component
class ReservationCancellationPolicy(
    private val reservationPaymentPort: ReservationPaymentService
) {
    fun shouldReleaseInventoryOnCancel(reservation: Reservation): Boolean {
        if (reservation.status == ReservationStatus.CONFIRMED) {
            return true
        }
        if (reservation.memberId == null) {
            return reservation.status == ReservationStatus.PENDING
        }

        val payment = reservationPaymentPort.findByReservationId(reservation.id)
            ?: return false
        return payment.status in inventoryRestorablePaymentStatuses
    }

    private companion object {
        val inventoryRestorablePaymentStatuses = setOf(
            ReservationPaymentStatus.APPROVED,
            ReservationPaymentStatus.CANCEL_REQUESTED,
            ReservationPaymentStatus.CANCEL_FAILED
        )
    }
}
