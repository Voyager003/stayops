package com.stayops.reservation.domain.event

import com.stayops.shared.domain.Money

data class ReservationCheckedOut(
    val reservationId: String,
    val propertyId: String,
    val guestId: String,
    val totalAmount: Money,
    val stayNights: Long
)
