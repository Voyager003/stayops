package com.stayops.reservation.domain.repository

import com.stayops.reservation.domain.model.ReservationIntent
import java.time.Instant
import java.time.LocalDate

interface ReservationIntentRepository {
    fun save(intent: ReservationIntent): ReservationIntent
    fun findById(id: String): ReservationIntent?
    fun findExpiredPaymentWaiting(now: Instant, limit: Int): List<ReservationIntent>
    fun existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
        memberId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        now: Instant
    ): Boolean
}
