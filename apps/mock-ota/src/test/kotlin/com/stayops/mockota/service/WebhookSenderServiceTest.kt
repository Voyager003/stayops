package com.stayops.mockota.service

import com.stayops.mockota.model.MockBooking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebhookSenderServiceTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var service: WebhookSenderService
    private val objectMapper = JsonMapper.builder().findAndAddModules().build()

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = "http://localhost:${mockServer.port}/api/v1/properties/{propertyId}/channels/webhook/{channelCode}"
        service = WebhookSenderService(baseUrl)
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Nested
    inner class SendBookingWebhook {
        @Test
        fun `should send POST to correct URL with propertyId and channelCode`() {
            mockServer.enqueue(MockResponse().setResponseCode(200))

            service.sendBookingWebhook(
                propertyId = "prop-1",
                channelCode = "AGODA",
                webhookSecret = "secret",
                booking = createBooking()
            )

            val request = mockServer.takeRequest()
            assertEquals("POST", request.method)
            assertTrue(request.path!!.contains("/prop-1/"))
            assertTrue(request.path!!.contains("/AGODA"))
        }

        @Test
        fun `should include BOOKING eventType in payload`() {
            mockServer.enqueue(MockResponse().setResponseCode(200))

            service.sendBookingWebhook(
                propertyId = "prop-1",
                channelCode = "AGODA",
                webhookSecret = "secret",
                booking = createBooking()
            )

            val request = mockServer.takeRequest()
            val body = objectMapper.readValue(request.body.readUtf8(), Map::class.java)
            assertEquals("BOOKING", body["eventType"])
            assertNotNull(body["eventId"])
            assertNotNull(body["timestamp"])
        }

        @Test
        fun `should include booking details in payload`() {
            mockServer.enqueue(MockResponse().setResponseCode(200))

            val booking = MockBooking(
                bookingId = "b-001",
                roomTypeCode = "DLX",
                checkInDate = LocalDate.of(2026, 4, 1),
                checkOutDate = LocalDate.of(2026, 4, 3),
                guestName = "Jane Doe"
            )
            service.sendBookingWebhook("prop-1", "AGODA", "secret", booking)

            val request = mockServer.takeRequest()
            val body = objectMapper.readValue(request.body.readUtf8(), Map::class.java)
            @Suppress("UNCHECKED_CAST")
            val bookingData = body["booking"] as Map<String, Any>
            assertEquals("b-001", bookingData["bookingId"])
            assertEquals("DLX", bookingData["roomTypeCode"])
            assertEquals("2026-04-01", bookingData["checkInDate"])
            assertEquals("2026-04-03", bookingData["checkOutDate"])
            assertEquals("Jane Doe", bookingData["guestName"])
        }
    }

    @Nested
    inner class SendCancellationWebhook {
        @Test
        fun `should include CANCELLATION eventType and bookingId`() {
            mockServer.enqueue(MockResponse().setResponseCode(200))

            service.sendCancellationWebhook("prop-1", "AGODA", "secret", "booking-999")

            val request = mockServer.takeRequest()
            val body = objectMapper.readValue(request.body.readUtf8(), Map::class.java)
            assertEquals("CANCELLATION", body["eventType"])
            assertEquals("booking-999", body["bookingId"])
            assertNotNull(body["eventId"])
        }
    }

    @Nested
    inner class HmacSignatureVerification {
        @Test
        fun `should include X-Webhook-Signature header with sha256 prefix`() {
            mockServer.enqueue(MockResponse().setResponseCode(200))

            service.sendBookingWebhook("prop-1", "AGODA", "my-secret", createBooking())

            val request = mockServer.takeRequest()
            val signature = request.getHeader("X-Webhook-Signature")
            assertNotNull(signature)
            assertTrue(signature.startsWith("sha256="))
        }

        @Test
        fun `should produce valid HMAC-SHA256 signature matching PMS verifier logic`() {
            mockServer.enqueue(MockResponse().setResponseCode(200))

            val secret = "test-webhook-secret"
            service.sendBookingWebhook("prop-1", "AGODA", secret, createBooking())

            val request = mockServer.takeRequest()
            val bodyString = request.body.clone().readUtf8()
            val signature = request.getHeader("X-Webhook-Signature")!!

            // Reproduce PMS HmacSignatureVerifier logic
            val expectedHash = hmacSha256(secret, bodyString)
            assertEquals("sha256=$expectedHash", signature)
        }

        @Test
        fun `should produce different signatures for different secrets`() {
            mockServer.enqueue(MockResponse().setResponseCode(200))
            mockServer.enqueue(MockResponse().setResponseCode(200))

            val booking = createBooking()
            service.sendBookingWebhook("prop-1", "AGODA", "secret-A", booking)
            service.sendBookingWebhook("prop-1", "AGODA", "secret-B", booking)

            val sig1 = mockServer.takeRequest().getHeader("X-Webhook-Signature")
            val sig2 = mockServer.takeRequest().getHeader("X-Webhook-Signature")
            assertTrue(sig1 != sig2, "Different secrets should produce different signatures")
        }

        /**
         * Reproduces the same HMAC-SHA256 algorithm used by PMS HmacSignatureVerifier
         * to verify cross-system signature compatibility.
         */
        private fun hmacSha256(secret: String, data: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    private fun createBooking() = MockBooking(
        bookingId = "test-booking-id",
        roomTypeCode = "STD",
        checkInDate = LocalDate.of(2026, 4, 1),
        checkOutDate = LocalDate.of(2026, 4, 3),
        guestName = "Test Guest"
    )
}
