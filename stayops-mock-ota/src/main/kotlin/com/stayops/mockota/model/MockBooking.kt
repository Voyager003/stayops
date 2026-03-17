package com.stayops.mockota.model

import java.time.LocalDate
import java.util.UUID

data class MockBooking(
    val bookingId: String = UUID.randomUUID().toString(),
    val roomTypeCode: String,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val guestName: String
)
