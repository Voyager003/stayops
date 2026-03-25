package com.stayops.mockota.api

import com.stayops.mockota.service.FailureMode
import com.stayops.mockota.service.FailureSimulatorService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
class AriReceiverApiTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var failureSimulator: FailureSimulatorService

    @Autowired
    private lateinit var ariReceiverApi: AriReceiverApi

    @BeforeEach
    fun setUp() {
        ariReceiverApi.clearReceived()
        failureSimulator.clearAll()
    }

    @Nested
    inner class ReceiveAvailability {
        @Test
        fun `should accept valid ARI payload`() {
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":5}"""
                header("X-Idempotency-Key", "key-001")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("accepted") }
                jsonPath("$.idempotencyKey") { value("key-001") }
            }
        }

        @Test
        fun `should store received ARI`() {
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"DLX","date":"2026-04-02","availableCount":3}"""
                header("X-Idempotency-Key", "key-002")
            }

            mockMvc.get("/api/v1/ari/received").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].roomTypeCode") { value("DLX") }
                jsonPath("$[0].date") { value("2026-04-02") }
                jsonPath("$[0].availableCount") { value(3) }
            }
        }

        @Test
        fun `should return duplicate for same idempotency key`() {
            val payload = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":5}"""

            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = payload
                header("X-Idempotency-Key", "dup-key")
            }

            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = payload
                header("X-Idempotency-Key", "dup-key")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("duplicate") }
            }
        }

        @Test
        fun `should accept requests without idempotency key`() {
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":2}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("accepted") }
            }
        }

        @Test
        fun `should not deduplicate when idempotency key is absent`() {
            val payload = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":2}"""

            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = payload
            }
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = payload
            }

            mockMvc.get("/api/v1/ari/received").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
        }
    }

    @Nested
    inner class FailureModeSimulation {
        @Test
        fun `should return 504 for timeout mode`() {
            failureSimulator.setFailureMode(FailureMode("TIMEOUT", delayMs = 0))

            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":1}"""
            }.andExpect {
                status { isGatewayTimeout() }
                jsonPath("$.error") { value("timeout") }
            }
        }

        @Test
        fun `should return 503 for server error mode`() {
            failureSimulator.setFailureMode(FailureMode.serverError())

            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":1}"""
            }.andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.error") { value("service unavailable") }
            }
        }

        @Test
        fun `should return 429 for rate limit mode`() {
            failureSimulator.setFailureMode(FailureMode.rateLimit())

            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":1}"""
            }.andExpect {
                status { isTooManyRequests() }
                jsonPath("$.error") { value("rate limit exceeded") }
            }
        }

        @Test
        fun `should not store ARI when failure mode is active`() {
            failureSimulator.setFailureMode(FailureMode.serverError())

            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":1}"""
            }

            mockMvc.get("/api/v1/ari/received").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
        }
    }

    @Nested
    inner class ClearReceived {
        @Test
        fun `should clear all received ARI`() {
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":5}"""
            }

            mockMvc.post("/api/v1/ari/clear")

            mockMvc.get("/api/v1/ari/received").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
        }
    }

    @Nested
    inner class GetReceivedAri {
        @Test
        fun `should return empty list initially`() {
            mockMvc.get("/api/v1/ari/received").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
        }

        @Test
        fun `should return multiple ARI records in order`() {
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":5}"""
                header("X-Idempotency-Key", "k1")
            }
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"DLX","date":"2026-04-02","availableCount":3}"""
                header("X-Idempotency-Key", "k2")
            }

            mockMvc.get("/api/v1/ari/received").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].roomTypeCode") { value("STD") }
                jsonPath("$[1].roomTypeCode") { value("DLX") }
            }
        }
    }
}
