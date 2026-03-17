package com.stayops.channel.api.dto

import java.time.Instant
import java.time.LocalDate

sealed interface WebhookEvent {
    val eventId: String
    val eventType: String
    val timestamp: Instant
}

data class OtaBookingEvent(
    override val eventId: String,
    override val eventType: String = "BOOKING",
    override val timestamp: Instant,
    val bookingId: String,
    val roomTypeCode: String,
    val checkInDate: LocalDate,
    val checkOutDate: LocalDate,
    val guestName: String
) : WebhookEvent

data class OtaCancellationEvent(
    override val eventId: String,
    override val eventType: String = "CANCELLATION",
    override val timestamp: Instant,
    val bookingId: String
) : WebhookEvent
