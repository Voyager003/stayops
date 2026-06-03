package com.stayops.channel.domain.service

interface MockOtaSimulationPort {
    fun simulateRandomBooking(endpoint: String, propertyId: String, channelCode: String): MockOtaRandomBookingResult
}

data class MockOtaRandomBookingResult(
    val status: String,
    val bookingId: String,
    val roomTypeId: String,
    val date: String,
    val guestName: String
)
