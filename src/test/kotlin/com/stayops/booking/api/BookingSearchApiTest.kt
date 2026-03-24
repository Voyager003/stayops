package com.stayops.booking.api

import com.stayops.booking.application.service.BookingSearchApplication
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.property.domain.model.*
import com.stayops.room.domain.model.RoomType
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.GlobalExceptionHandler
import com.stayops.shared.exception.NotFoundException
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate

class BookingSearchApiTest {

    private val bookingSearchApplication = mockk<BookingSearchApplication>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(BookingSearchApi(bookingSearchApplication))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    private fun sampleProperty() = Property.create(
        id = "prop-1",
        ownerId = "owner-1",
        name = "테스트 호텔",
        type = PropertyType.HOTEL,
        address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
        contactInfo = ContactInfo.of("02-1234-5678", "hotel@test.com"),
        description = "테스트 호텔입니다",
        timezone = "Asia/Seoul",
        currency = "KRW"
    ).activate()

    private fun sampleRoomType() = RoomType.create(
        id = "rt-1",
        propertyId = "prop-1",
        name = "디럭스룸",
        description = "넓고 쾌적한 객실",
        maxOccupancy = 2,
        basePrice = Money.won(100_000),
        amenities = listOf("WiFi", "TV")
    )

    @Nested
    inner class `숙소_목록_조회` {

        @Test
        fun `ACTIVE 숙소 목록을 반환한다`() {
            every { bookingSearchApplication.searchProperties() } returns listOf(sampleProperty())

            mockMvc.get("/api/v1/booking/properties")
                .andExpect {
                    status { isOk() }
                    jsonPath("$[0].name") { value("테스트 호텔") }
                    jsonPath("$[0].status") { value("ACTIVE") }
                }
        }
    }

    @Nested
    inner class `숙소_상세_조회` {

        @Test
        fun `숙소 상세 정보를 반환한다`() {
            every { bookingSearchApplication.getProperty("prop-1") } returns sampleProperty()

            mockMvc.get("/api/v1/booking/properties/prop-1")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.name") { value("테스트 호텔") }
                    jsonPath("$.type") { value("HOTEL") }
                }
        }

        @Test
        fun `존재하지 않는 숙소면 404를 반환한다`() {
            every { bookingSearchApplication.getProperty("prop-999") } throws
                NotFoundException("PROPERTY_NOT_FOUND", "숙소를 찾을 수 없습니다")

            mockMvc.get("/api/v1/booking/properties/prop-999")
                .andExpect {
                    status { isNotFound() }
                }
        }
    }

    @Nested
    inner class `객실타입_조회` {

        @Test
        fun `ACTIVE 객실타입 목록을 반환한다`() {
            every { bookingSearchApplication.searchRoomTypes("prop-1") } returns listOf(sampleRoomType())

            mockMvc.get("/api/v1/booking/properties/prop-1/room-types")
                .andExpect {
                    status { isOk() }
                    jsonPath("$[0].name") { value("디럭스룸") }
                    jsonPath("$[0].maxOccupancy") { value(2) }
                }
        }
    }

    @Nested
    inner class `가용_재고_조회` {

        @Test
        fun `날짜별 가용 재고를 반환한다`() {
            val date = LocalDate.of(2026, 4, 1)
            val inventory = RoomInventory.create(
                id = "inv-1",
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                date = date,
                totalCount = 5
            )
            every {
                bookingSearchApplication.getAvailability("prop-1", "rt-1", date, date.plusDays(2))
            } returns listOf(inventory)

            mockMvc.get("/api/v1/booking/properties/prop-1/room-types/rt-1/availability") {
                param("checkIn", "2026-04-01")
                param("checkOut", "2026-04-03")
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].date") { value("2026-04-01") }
                jsonPath("$[0].availableCount") { value(5) }
            }
        }
    }

    @Nested
    inner class `요금_미리보기` {

        @Test
        fun `총 요금을 반환한다`() {
            val checkIn = LocalDate.of(2026, 4, 1)
            val checkOut = LocalDate.of(2026, 4, 3)
            every {
                bookingSearchApplication.getRatePreview("prop-1", "rt-1", checkIn, checkOut)
            } returns Money.won(200_000)

            mockMvc.get("/api/v1/booking/properties/prop-1/room-types/rt-1/rates") {
                param("checkIn", "2026-04-01")
                param("checkOut", "2026-04-03")
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalAmount") { value(200000) }
                jsonPath("$.nights") { value(2) }
            }
        }
    }
}
