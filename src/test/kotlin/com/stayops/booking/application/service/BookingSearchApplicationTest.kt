package com.stayops.booking.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolver
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate

class BookingSearchApplicationTest : BehaviorSpec({

    val propertyRepository = mockk<PropertyRepository>()
    val roomTypeRepository = mockk<RoomTypeRepository>()
    val inventoryRepository = mockk<RoomInventoryRepository>()
    val ratePlanRepository = mockk<RatePlanRepository>()
    val rateResolver = RateResolver()

    val service = BookingSearchApplication(
        propertyRepository = propertyRepository,
        roomTypeRepository = roomTypeRepository,
        inventoryRepository = inventoryRepository,
        ratePlanRepository = ratePlanRepository,
        rateResolver = rateResolver
    )

    fun activeProperty(id: String = "prop-1") = Property.create(
        id = id,
        ownerId = "owner-1",
        name = "테스트 호텔",
        type = PropertyType.HOTEL,
        address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
        contactInfo = ContactInfo.of("02-1234-5678", "hotel@test.com"),
        description = "테스트 호텔입니다",
        timezone = "Asia/Seoul",
        currency = "KRW"
    ).activate()

    fun inactiveProperty(id: String = "prop-2") = Property.create(
        id = id,
        ownerId = "owner-1",
        name = "비활성 호텔",
        type = PropertyType.HOTEL,
        address = Address.of("서울시 종로구", "서울", "서울", "03000", "KR"),
        contactInfo = ContactInfo.of("02-5678-1234", "inactive@test.com"),
        description = "비활성 호텔입니다",
        timezone = "Asia/Seoul",
        currency = "KRW"
    )

    fun activeRoomType(id: String = "rt-1", propertyId: String = "prop-1") = RoomType.create(
        id = id,
        propertyId = propertyId,
        name = "디럭스룸",
        description = "넓고 쾌적한 객실",
        maxOccupancy = 2,
        basePrice = Money.won(100_000),
        amenities = listOf("WiFi", "TV")
    )

    fun inactiveRoomType(id: String = "rt-2", propertyId: String = "prop-1") = RoomType.create(
        id = id,
        propertyId = propertyId,
        name = "이코노미룸",
        description = "경제적인 객실",
        maxOccupancy = 2,
        basePrice = Money.won(50_000)
    )

    fun inventory(propertyId: String, roomTypeId: String, date: LocalDate, totalCount: Int = 5, reservedCount: Int = 0) =
        RoomInventory.create(
            id = "inv-$date",
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            date = date,
            totalCount = totalCount
        )

    // -- 숙소 목록 조회 --

    given("숙소 목록 조회 시") {

        `when`("ACTIVE 숙소와 INACTIVE 숙소가 있으면") {
            every { propertyRepository.findAll() } returns listOf(
                activeProperty("prop-1"),
                inactiveProperty("prop-2"),
                activeProperty("prop-3")
            )

            val result = service.searchProperties()

            then("ACTIVE 숙소만 반환된다") {
                result shouldHaveSize 2
                result.all { it.status == PropertyStatus.ACTIVE } shouldBe true
            }
        }
    }

    // -- 숙소 상세 조회 --

    given("숙소 상세 조회 시") {

        `when`("ACTIVE 숙소를 조회하면") {
            every { propertyRepository.findById("prop-1") } returns activeProperty()

            val result = service.getProperty("prop-1")

            then("숙소 정보를 반환한다") {
                result.name shouldBe "테스트 호텔"
            }
        }

        `when`("INACTIVE 숙소를 조회하면") {
            every { propertyRepository.findById("prop-2") } returns inactiveProperty()

            then("예외가 발생한다") {
                shouldThrow<Exception> {
                    service.getProperty("prop-2")
                }
            }
        }

        `when`("존재하지 않는 숙소를 조회하면") {
            every { propertyRepository.findById("prop-999") } returns null

            then("예외가 발생한다") {
                shouldThrow<Exception> {
                    service.getProperty("prop-999")
                }
            }
        }
    }

    // -- 객실타입 목록 조회 --

    given("객실타입 목록 조회 시") {

        `when`("여러 객실타입이 있으면") {
            every { propertyRepository.findById("prop-1") } returns activeProperty()
            every { roomTypeRepository.findByPropertyId("prop-1") } returns listOf(
                activeRoomType("rt-1"),
                inactiveRoomType("rt-2")
            )

            val result = service.searchRoomTypes("prop-1")

            then("모든 객실타입을 반환한다") {
                result shouldHaveSize 2
                result[0].name shouldBe "디럭스룸"
                result[1].name shouldBe "이코노미룸"
            }
        }
    }

    // -- 가용 재고 조회 --

    given("날짜별 가용 재고 조회 시") {
        val checkIn = LocalDate.of(2026, 4, 1)
        val checkOut = LocalDate.of(2026, 4, 3)

        `when`("재고가 존재하면") {
            every { propertyRepository.findById("prop-1") } returns activeProperty()
            every {
                inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                    "prop-1", "rt-1", checkIn, checkOut.minusDays(1)
                )
            } returns listOf(
                inventory("prop-1", "rt-1", checkIn),
                inventory("prop-1", "rt-1", checkIn.plusDays(1))
            )

            val result = service.getAvailability("prop-1", "rt-1", checkIn, checkOut)

            then("날짜별 가용 재고를 반환한다") {
                result shouldHaveSize 2
                result.all { it.availableCount == 5 } shouldBe true
            }
        }
    }

    // -- 요금 미리보기 --

    given("요금 미리보기 조회 시") {
        val checkIn = LocalDate.of(2026, 4, 1)
        val checkOut = LocalDate.of(2026, 4, 3)

        `when`("요금제가 없으면 기본 요금이 적용된다") {
            every { propertyRepository.findById("prop-1") } returns activeProperty()
            every { roomTypeRepository.findById("rt-1") } returns activeRoomType()
            every {
                ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
                    "prop-1", "rt-1", RatePlanStatus.ACTIVE
                )
            } returns emptyList()

            val result = service.getRatePreview("prop-1", "rt-1", checkIn, checkOut)

            then("basePrice × 박수로 계산된다") {
                result shouldBe Money.won(200_000) // 100,000 × 2박
            }
        }
    }
})
