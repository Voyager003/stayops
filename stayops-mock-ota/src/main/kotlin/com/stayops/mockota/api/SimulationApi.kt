package com.stayops.mockota.api

import com.stayops.mockota.model.MockBooking
import com.stayops.mockota.service.FailureMode
import com.stayops.mockota.service.FailureSimulatorService
import com.stayops.mockota.service.WebhookSenderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/simulate")
class SimulationApi(
    private val webhookSender: WebhookSenderService,
    private val failureSimulator: FailureSimulatorService
) {

    data class BookingSimulationRequest(
        val propertyId: String,
        val channelCode: String,
        val webhookSecret: String,
        val roomTypeCode: String,
        val checkInDate: LocalDate,
        val checkOutDate: LocalDate,
        val guestName: String
    )

    data class CancellationSimulationRequest(
        val propertyId: String,
        val channelCode: String,
        val webhookSecret: String,
        val bookingId: String
    )

    data class FailureModeRequest(
        val type: String,
        val delayMs: Long = 0
    )

    @PostMapping("/booking")
    fun simulateBooking(@RequestBody request: BookingSimulationRequest): ResponseEntity<Map<String, String>> {
        val booking = MockBooking(
            roomTypeCode = request.roomTypeCode,
            checkInDate = request.checkInDate,
            checkOutDate = request.checkOutDate,
            guestName = request.guestName
        )
        webhookSender.sendBookingWebhook(
            propertyId = request.propertyId,
            channelCode = request.channelCode,
            webhookSecret = request.webhookSecret,
            booking = booking
        )
        return ResponseEntity.ok(mapOf("status" to "sent", "bookingId" to booking.bookingId))
    }

    @PostMapping("/cancellation")
    fun simulateCancellation(@RequestBody request: CancellationSimulationRequest): ResponseEntity<Map<String, String>> {
        webhookSender.sendCancellationWebhook(
            propertyId = request.propertyId,
            channelCode = request.channelCode,
            webhookSecret = request.webhookSecret,
            bookingId = request.bookingId
        )
        return ResponseEntity.ok(mapOf("status" to "sent"))
    }

    @PostMapping("/failure-mode")
    fun setFailureMode(@RequestBody request: FailureModeRequest): ResponseEntity<Map<String, String>> {
        failureSimulator.setFailureMode(FailureMode(type = request.type, delayMs = request.delayMs))
        return ResponseEntity.ok(mapOf("status" to "enabled", "type" to request.type))
    }

    @PostMapping("/failure-mode/clear")
    fun clearFailureMode(): ResponseEntity<Map<String, String>> {
        failureSimulator.clearAll()
        return ResponseEntity.ok(mapOf("status" to "cleared"))
    }
}
