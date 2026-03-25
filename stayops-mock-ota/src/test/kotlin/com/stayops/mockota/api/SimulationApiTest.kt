package com.stayops.mockota.api

import com.stayops.mockota.service.FailureSimulatorService
import com.stayops.mockota.service.WebhookSenderService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class SimulationApiTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var webhookSender: WebhookSenderService

    @Autowired
    private lateinit var failureSimulator: FailureSimulatorService

    @BeforeEach
    fun setUp() {
        failureSimulator.clearAll()
    }

    @Nested
    inner class SimulateBooking {
        @Test
        fun `should return 200 with bookingId`() {
            mockMvc.post("/api/v1/simulate/booking") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                        "propertyId": "prop-1",
                        "channelCode": "AGODA",
                        "webhookSecret": "secret123",
                        "roomTypeCode": "STD",
                        "checkInDate": "2026-04-01",
                        "checkOutDate": "2026-04-03",
                        "guestName": "John Doe"
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("sent") }
                jsonPath("$.bookingId") { exists() }
            }
        }
    }

    @Nested
    inner class SimulateCancellation {
        @Test
        fun `should return 200 with sent status`() {
            mockMvc.post("/api/v1/simulate/cancellation") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                        "propertyId": "prop-1",
                        "channelCode": "AGODA",
                        "webhookSecret": "secret123",
                        "bookingId": "booking-001"
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("sent") }
            }
        }
    }

    @Nested
    inner class FailureModeControl {
        @Test
        fun `should enable failure mode and return enabled status`() {
            mockMvc.post("/api/v1/simulate/failure-mode") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"type":"SERVER_ERROR","delayMs":0}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("enabled") }
                jsonPath("$.type") { value("SERVER_ERROR") }
            }

            assertTrue(failureSimulator.shouldFail())
        }

        @Test
        fun `should clear failure mode and return cleared status`() {
            failureSimulator.setFailureMode(
                com.stayops.mockota.service.FailureMode.serverError()
            )

            mockMvc.post("/api/v1/simulate/failure-mode/clear").andExpect {
                status { isOk() }
                jsonPath("$.status") { value("cleared") }
            }

            assertFalse(failureSimulator.shouldFail())
        }
    }
}
