package com.stayops.channel.application.required

import java.time.LocalDate

interface MockOtaBookingSimulator {
    fun simulateRandomBooking(endpoint: String, propertyId: String, channelCode: String): MockOtaRandomBookingResult
    fun simulateInventoryBooking(
        endpoint: String,
        propertyId: String,
        channelCode: String,
        roomTypeCode: String,
        date: LocalDate
    ): MockOtaRandomBookingResult
}

data class MockOtaRandomBookingResult(
    val status: String,
    val bookingId: String,
    val roomTypeId: String,
    val date: String,
    val guestName: String
)
