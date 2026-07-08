package com.stayops.mockota.api

import com.stayops.mockota.TestcontainersConfiguration
import com.stayops.mockota.model.OtaInventory
import com.stayops.mockota.dao.OtaInventoryDao
import com.stayops.mockota.service.FailureSimulatorService
import com.stayops.mockota.service.WebhookSenderService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import com.stayops.mockota.model.MockBooking
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class SimulationApiTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var webhookSender: WebhookSenderService

    @Autowired
    private lateinit var failureSimulator: FailureSimulatorService

    @Autowired
    private lateinit var otaInventoryDao: OtaInventoryDao

    @BeforeEach
    fun setUp() {
        failureSimulator.clearAll()
        otaInventoryDao.deleteAll()
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
    inner class SimulateRandomBooking {

        @Test
        fun `예약 가능한 재고가 있으면 랜덤 예약을 발생시키고 OTA 재고를 차감한다`() {
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "YANOLJA", roomTypeId = "rt-1", date = "2026-04-05", availableCount = 3))

            mockMvc.post("/api/v1/simulate/random-booking") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"propertyId":"prop-1","channelCode":"YANOLJA"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("sent") }
                jsonPath("$.roomTypeId") { value("rt-1") }
                jsonPath("$.date") { value("2026-04-05") }
            }

            val updated = otaInventoryDao.findByPropertyIdAndChannelCodeAndRoomTypeIdAndDate("prop-1", "YANOLJA", "rt-1", "2026-04-05")!!
            assertEquals(2, updated.availableCount)
        }

        @Test
        fun `예약 가능한 재고가 없으면 400을 반환한다`() {
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "YANOLJA", roomTypeId = "rt-1", date = "2026-04-05", availableCount = 0))

            mockMvc.post("/api/v1/simulate/random-booking") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"propertyId":"prop-1","channelCode":"YANOLJA"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }
        }

        @Test
        fun `재고가 1인 상태에서 예약하면 0이 되고 다음 예약은 400을 반환한다`() {
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "YANOLJA", roomTypeId = "rt-1", date = "2026-04-05", availableCount = 1))

            // 첫 예약 성공
            mockMvc.post("/api/v1/simulate/random-booking") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"propertyId":"prop-1","channelCode":"YANOLJA"}"""
            }.andExpect {
                status { isOk() }
            }

            val updated = otaInventoryDao.findByPropertyIdAndChannelCodeAndRoomTypeIdAndDate("prop-1", "YANOLJA", "rt-1", "2026-04-05")!!
            assertEquals(0, updated.availableCount)

            // 두 번째 예약 실패
            mockMvc.post("/api/v1/simulate/random-booking") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"propertyId":"prop-1","channelCode":"YANOLJA"}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `여러 객실타입이 있을 때 선택된 결과는 후보 중 하나이다`() {
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "YANOLJA", roomTypeId = "rt-1", date = "2026-04-05", availableCount = 2))
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "YANOLJA", roomTypeId = "rt-2", date = "2026-04-06", availableCount = 3))
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "YANOLJA", roomTypeId = "rt-3", date = "2026-04-07", availableCount = 0)) // 제외 대상

            val result = mockMvc.post("/api/v1/simulate/random-booking") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"propertyId":"prop-1","channelCode":"YANOLJA"}"""
            }.andExpect {
                status { isOk() }
            }.andReturn()

            val body = result.response.contentAsString
            val selectedRoomTypeId = body.substringAfter("\"roomTypeId\":\"").substringBefore("\"")
            assertTrue(selectedRoomTypeId in listOf("rt-1", "rt-2"), "선택된 roomTypeId($selectedRoomTypeId)가 후보에 포함되어야 한다")
        }

        @Test
        fun `예약 발생 시 웹훅이 전송된다`() {
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "YANOLJA", roomTypeId = "rt-1", date = "2026-04-05", availableCount = 1))

            mockMvc.post("/api/v1/simulate/random-booking") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"propertyId":"prop-1","channelCode":"YANOLJA"}"""
            }.andExpect {
                status { isOk() }
            }

            val captor = argumentCaptor<MockBooking>()
            verify(webhookSender).sendBookingWebhook(
                eq("prop-1"),
                eq("YANOLJA"),
                eq("YANOLJA"),
                captor.capture()
            )
            assertEquals("rt-1", captor.firstValue.roomTypeCode)
        }

        @Test
        fun `랜덤 예약은 요청한 숙소와 채널의 재고만 선택한다`() {
            otaInventoryDao.save(OtaInventory(propertyId = "other-prop", channelCode = "YANOLJA", roomTypeId = "rt-other-prop", date = "2026-04-05", availableCount = 5))
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "OTHER", roomTypeId = "rt-other-channel", date = "2026-04-05", availableCount = 5))
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "YANOLJA", roomTypeId = "rt-target", date = "2026-04-06", availableCount = 1))

            mockMvc.post("/api/v1/simulate/random-booking") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"propertyId":"prop-1","channelCode":"YANOLJA"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.roomTypeId") { value("rt-target") }
                jsonPath("$.date") { value("2026-04-06") }
            }

            val target = otaInventoryDao.findByPropertyIdAndChannelCodeAndRoomTypeIdAndDate("prop-1", "YANOLJA", "rt-target", "2026-04-06")!!
            val otherProp = otaInventoryDao.findByPropertyIdAndChannelCodeAndRoomTypeIdAndDate("other-prop", "YANOLJA", "rt-other-prop", "2026-04-05")!!
            val otherChannel = otaInventoryDao.findByPropertyIdAndChannelCodeAndRoomTypeIdAndDate("prop-1", "OTHER", "rt-other-channel", "2026-04-05")!!
            assertEquals(0, target.availableCount)
            assertEquals(5, otherProp.availableCount)
            assertEquals(5, otherChannel.availableCount)
        }
    }

    @Nested
    inner class SimulateInventoryBooking {
        @Test
        fun `지정한 OTA 재고만 차감하고 웹훅을 전송한다`() {
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "YANOLJA", roomTypeId = "rt-target", date = "2026-04-06", availableCount = 1))
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "OTHER", roomTypeId = "rt-target", date = "2026-04-06", availableCount = 4))

            mockMvc.post("/api/v1/simulate/inventory-booking") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"propertyId":"prop-1","channelCode":"YANOLJA","roomTypeCode":"rt-target","date":"2026-04-06"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("sent") }
                jsonPath("$.roomTypeId") { value("rt-target") }
                jsonPath("$.date") { value("2026-04-06") }
            }

            val target = otaInventoryDao.findByPropertyIdAndChannelCodeAndRoomTypeIdAndDate("prop-1", "YANOLJA", "rt-target", "2026-04-06")!!
            val otherChannel = otaInventoryDao.findByPropertyIdAndChannelCodeAndRoomTypeIdAndDate("prop-1", "OTHER", "rt-target", "2026-04-06")!!
            assertEquals(0, target.availableCount)
            assertEquals(4, otherChannel.availableCount)

            val captor = argumentCaptor<MockBooking>()
            verify(webhookSender).sendBookingWebhook(eq("prop-1"), eq("YANOLJA"), eq("YANOLJA"), captor.capture())
            assertEquals("rt-target", captor.firstValue.roomTypeCode)
        }

        @Test
        fun `지정한 OTA 재고가 없으면 웹훅을 보내지 않고 400을 반환한다`() {
            otaInventoryDao.save(OtaInventory(propertyId = "prop-1", channelCode = "YANOLJA", roomTypeId = "rt-target", date = "2026-04-06", availableCount = 0))

            mockMvc.post("/api/v1/simulate/inventory-booking") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"propertyId":"prop-1","channelCode":"YANOLJA","roomTypeCode":"rt-target","date":"2026-04-06"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { exists() }
            }

            verify(webhookSender, never()).sendBookingWebhook(any(), any(), any(), any())
            val target = otaInventoryDao.findByPropertyIdAndChannelCodeAndRoomTypeIdAndDate("prop-1", "YANOLJA", "rt-target", "2026-04-06")!!
            assertEquals(0, target.availableCount)
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
