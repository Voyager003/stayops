package com.stayops.channel.api.dto

import com.stayops.channel.domain.service.MockOtaRandomBookingResult

data class RandomBookingSimulationResponse(
    val status: String,
    val bookingId: String,
    val roomTypeId: String,
    val date: String,
    val guestName: String
) {
    companion object {
        fun from(result: MockOtaRandomBookingResult) = RandomBookingSimulationResponse(
            status = result.status,
            bookingId = result.bookingId,
            roomTypeId = result.roomTypeId,
            date = result.date,
            guestName = result.guestName
        )
    }
}
