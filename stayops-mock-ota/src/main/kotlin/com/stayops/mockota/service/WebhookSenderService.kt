package com.stayops.mockota.service

import com.stayops.mockota.model.MockBooking
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class WebhookSenderService(
    @Value("\${mock-ota.pms-webhook-url}") private val pmsWebhookUrl: String
) {

    private val restClient = RestClient.create()

    fun sendBookingWebhook(
        propertyId: String,
        channelCode: String,
        webhookSecret: String,
        booking: MockBooking
    ) {
        val event = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "eventType" to "BOOKING",
            "timestamp" to Instant.now().toString(),
            "booking" to mapOf(
                "bookingId" to booking.bookingId,
                "roomTypeCode" to booking.roomTypeCode,
                "checkInDate" to booking.checkInDate.toString(),
                "checkOutDate" to booking.checkOutDate.toString(),
                "guestName" to booking.guestName
            )
        )
        sendWebhook(propertyId, channelCode, webhookSecret, event)
    }

    fun sendCancellationWebhook(
        propertyId: String,
        channelCode: String,
        webhookSecret: String,
        bookingId: String
    ) {
        val event = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "eventType" to "CANCELLATION",
            "timestamp" to Instant.now().toString(),
            "bookingId" to bookingId
        )
        sendWebhook(propertyId, channelCode, webhookSecret, event)
    }

    private fun sendWebhook(
        propertyId: String,
        channelCode: String,
        webhookSecret: String,
        payload: Map<String, Any>
    ) {
        val url = pmsWebhookUrl
            .replace("{propertyId}", propertyId)
            .replace("{channelCode}", channelCode)

        val bodyJson = JsonMapper.builder()
            .findAndAddModules()
            .build()
            .writeValueAsString(payload)

        val signature = hmacSha256(webhookSecret, bodyJson)

        restClient.post()
            .uri(url)
            .header("Content-Type", "application/json")
            .header("X-Webhook-Signature", "sha256=$signature")
            .body(bodyJson)
            .retrieve()
            .toBodilessEntity()
    }

    private fun hmacSha256(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
