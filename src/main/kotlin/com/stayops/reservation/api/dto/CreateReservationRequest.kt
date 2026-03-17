package com.stayops.reservation.api.dto

import java.time.LocalDate

data class CreateReservationRequest(
    val roomTypeId: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val numberOfGuests: Int,
    val guestId: String,
    val guestName: String,
    val guestPhone: String,
    val guestEmail: String? = null,
    val channelCode: String,
    val externalReservationId: String? = null
)
