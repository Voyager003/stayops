package com.stayops.reservation.domain.repository

import com.stayops.reservation.domain.model.ActiveReservationAttempt

interface ActiveReservationAttemptRepository {
    fun save(attempt: ActiveReservationAttempt): ActiveReservationAttempt
    fun findByBookingKey(bookingKey: String): ActiveReservationAttempt?
    fun deleteByReservationIntentId(reservationIntentId: String)
}
