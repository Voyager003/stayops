package com.stayops.reservation.api.customer

import com.stayops.reservation.application.service.ReservationSearchApplication
import com.stayops.reservation.application.service.ReservationOffer
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

class ReservationSearchApiTest {

    private val reservationSearchApplication = mockk<ReservationSearchApplication>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ReservationSearchApi(reservationSearchApplication))
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
            every { reservationSearchApplication.searchProperties() } returns listOf(sampleProperty())

            mockMvc.get("/api/v1/customer/properties")
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
            every { reservationSearchApplication.getProperty("prop-1") } returns sampleProperty()

            mockMvc.get("/api/v1/customer/properties/prop-1")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.name") { value("테스트 호텔") }
                    jsonPath("$.type") { value("HOTEL") }
                    jsonPath("$.timezone") { value("Asia/Seoul") }
                    jsonPath("$.currency") { value("KRW") }
                }
        }

        @Test
        fun `존재하지 않는 숙소면 404를 반환한다`() {
            every { reservationSearchApplication.getProperty("prop-999") } throws
                NotFoundException("PROPERTY_NOT_FOUND", "숙소를 찾을 수 없습니다")

            mockMvc.get("/api/v1/customer/properties/prop-999")
                .andExpect {
                    status { isNotFound() }
                }
        }
    }

    @Nested
    inner class `객실타입_조회` {

        @Test
        fun `ACTIVE 객실타입 목록을 반환한다`() {
            every { reservationSearchApplication.searchRoomTypes("prop-1") } returns listOf(sampleRoomType())

            mockMvc.get("/api/v1/customer/properties/prop-1/room-types")
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
            val inventory = RoomInventory.reconstitute(
                id = "inv-1",
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                date = date,
                totalCount = 5,
                reservedCount = 0,
                blockedCount = 0,
                version = 0L,
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now()
            )
            every {
                reservationSearchApplication.getAvailability("prop-1", "rt-1", date, date.plusDays(2))
            } returns listOf(inventory)

            mockMvc.get("/api/v1/customer/properties/prop-1/room-types/rt-1/availability") {
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
                reservationSearchApplication.getRatePreview("prop-1", "rt-1", checkIn, checkOut)
            } returns Money.won(200_000)

            mockMvc.get("/api/v1/customer/properties/prop-1/room-types/rt-1/rates") {
                param("checkIn", "2026-04-01")
                param("checkOut", "2026-04-03")
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalAmount") { value(200000) }
                jsonPath("$.nights") { value(2) }
            }
        }
    }

    @Nested
    inner class `공개_예약_Offer_조회` {

        @Test
        fun `객실별 예약 제안을 반환한다`() {
            val checkIn = LocalDate.of(2026, 4, 10)
            val checkOut = LocalDate.of(2026, 4, 12)
            every {
                reservationSearchApplication.getReservationOffers("prop-1", checkIn, checkOut, 3)
            } returns listOf(
                ReservationOffer(
                    roomType = sampleRoomType(),
                    availableCount = 1,
                    fitsGuests = false,
                    rateQuote = Money.won(200_000),
                    checkIn = checkIn,
                    checkOut = checkOut
                )
            )

            mockMvc.get("/api/v1/customer/properties/prop-1/offers") {
                param("checkIn", "2026-04-10")
                param("checkOut", "2026-04-12")
                param("guests", "3")
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].roomTypeId") { value("rt-1") }
                jsonPath("$[0].availableCount") { value(1) }
                jsonPath("$[0].fitsGuests") { value(false) }
                jsonPath("$[0].rateQuote.totalAmount") { value(200000) }
                jsonPath("$[0].rateQuote.nights") { value(2) }
            }
        }
    }
}
