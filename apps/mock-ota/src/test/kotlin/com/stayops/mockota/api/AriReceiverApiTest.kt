package com.stayops.mockota.api

import com.stayops.mockota.TestcontainersConfiguration
import com.stayops.mockota.dao.OtaInventoryDao
import com.stayops.mockota.service.FailureMode
import com.stayops.mockota.service.FailureSimulatorService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class AriReceiverApiTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var failureSimulator: FailureSimulatorService

    @Autowired
    private lateinit var ariReceiverApi: AriReceiverApi

    @Autowired
    private lateinit var otaInventoryDao: OtaInventoryDao

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
        fun `should store received ARI in MongoDB`() {
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"DLX","date":"2026-04-02","availableCount":3}"""
                header("X-Idempotency-Key", "key-002")
            }

            mockMvc.get("/api/v1/ari/received").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].roomTypeId") { value("DLX") }
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
        fun `should upsert when same roomTypeId and date sent without idempotency key`() {
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":2}"""
            }
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":7}"""
            }

            mockMvc.get("/api/v1/ari/received").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].availableCount") { value(7) }
            }
        }

        @Test
        fun `should store separate records for different roomTypeId or date`() {
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":2}"""
            }
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"DLX","date":"2026-04-01","availableCount":3}"""
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
        fun `should clear all received ARI from MongoDB`() {
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
        fun `should return multiple ARI records`() {
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
            }
        }
    }

    @Nested
    inner class GetInventory {
        @Test
        fun `should return inventory for roomTypeId within date range`() {
            // Seed data
            listOf("2026-04-01", "2026-04-02", "2026-04-03", "2026-04-04", "2026-04-05").forEachIndexed { i, date ->
                mockMvc.post("/api/v1/ari/availability") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"roomTypeCode":"STD","date":"$date","availableCount":${i + 1}}"""
                    header("X-Idempotency-Key", "inv-$i")
                }
            }

            mockMvc.get("/api/v1/ari/inventory") {
                param("roomTypeId", "STD")
                param("startDate", "2026-04-02")
                param("endDate", "2026-04-04")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) }
                jsonPath("$[0].date") { value("2026-04-02") }
                jsonPath("$[1].date") { value("2026-04-03") }
                jsonPath("$[2].date") { value("2026-04-04") }
            }
        }

        @Test
        fun `should return empty list when no matching inventory exists`() {
            mockMvc.get("/api/v1/ari/inventory") {
                param("roomTypeId", "NONEXISTENT")
                param("startDate", "2026-04-01")
                param("endDate", "2026-04-30")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
        }

        @Test
        fun `should filter by roomTypeId`() {
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"STD","date":"2026-04-01","availableCount":5}"""
                header("X-Idempotency-Key", "filter-1")
            }
            mockMvc.post("/api/v1/ari/availability") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"roomTypeCode":"DLX","date":"2026-04-01","availableCount":3}"""
                header("X-Idempotency-Key", "filter-2")
            }

            mockMvc.get("/api/v1/ari/inventory") {
                param("roomTypeId", "STD")
                param("startDate", "2026-04-01")
                param("endDate", "2026-04-01")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].roomTypeId") { value("STD") }
                jsonPath("$[0].availableCount") { value(5) }
            }
        }
    }
}
