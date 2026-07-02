package com.stayops.reservation.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate

class ReservationSearchApplicationTest : BehaviorSpec({

    val propertyRepository = mockk<PropertyRepository>()
    val roomTypeRepository = mockk<RoomTypeRepository>()
    val inventoryRepository = mockk<RoomInventoryRepository>()
    val ratePlanRepository = mockk<RatePlanRepository>()
    val rateResolverService = RateResolverService()

    val service = ReservationSearchApplication(
        propertyRepository = propertyRepository,
        roomTypeRepository = roomTypeRepository,
        inventoryRepository = inventoryRepository,
        ratePlanRepository = ratePlanRepository,
        rateResolverService = rateResolverService
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

    fun activePropertyWithAddress(
        id: String,
        street: String,
        city: String,
        state: String
    ) = Property.create(
        id = id,
        ownerId = "owner-1",
        name = "$city 호텔",
        type = PropertyType.HOTEL,
        address = Address.of(street, city, state, "06000", "KR"),
        contactInfo = ContactInfo.of("02-1234-5678", "hotel@test.com"),
        description = "$city 호텔입니다",
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
        RoomInventory.reconstitute(
            id = "inv-$date",
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            date = date,
            totalCount = totalCount,
            reservedCount = reservedCount,
            blockedCount = 0,
            version = 0,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z")
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

        `when`("지역 필터가 있으면") {
            every { propertyRepository.findAll() } returns listOf(
                activePropertyWithAddress("prop-1", "테헤란로 123", "서울", "강남구"),
                activePropertyWithAddress("prop-2", "해운대로 1", "부산", "해운대구")
            )

            val result = service.searchProperties(PropertySearchCriteria(region = "서울"))

            then("주소에 지역이 포함된 ACTIVE 숙소만 반환된다") {
                result.map { it.id } shouldContainExactly listOf("prop-1")
            }
        }

        `when`("인원 필터가 있으면") {
            every { propertyRepository.findAll() } returns listOf(
                activeProperty("prop-1"),
                activeProperty("prop-2")
            )
            every { roomTypeRepository.findByPropertyId("prop-1") } returns listOf(
                activeRoomType("rt-1", "prop-1")
            )
            every { roomTypeRepository.findByPropertyId("prop-2") } returns listOf(
                RoomType.create(
                    id = "rt-2",
                    propertyId = "prop-2",
                    name = "패밀리룸",
                    description = "가족 객실",
                    maxOccupancy = 4,
                    basePrice = Money.won(180_000)
                )
            )

            val result = service.searchProperties(PropertySearchCriteria(guests = 3))

            then("수용 가능한 객실 타입이 있는 숙소만 반환된다") {
                result.map { it.id } shouldContainExactly listOf("prop-2")
            }
        }

        `when`("날짜와 인원 필터가 함께 있으면") {
            val checkIn = LocalDate.of(2026, 5, 1)
            val checkOut = LocalDate.of(2026, 5, 3)
            every { propertyRepository.findAll() } returns listOf(
                activeProperty("prop-1"),
                activeProperty("prop-2")
            )
            every { roomTypeRepository.findByPropertyId("prop-1") } returns listOf(
                activeRoomType("rt-1", "prop-1")
            )
            every { roomTypeRepository.findByPropertyId("prop-2") } returns listOf(
                RoomType.create(
                    id = "rt-2",
                    propertyId = "prop-2",
                    name = "패밀리룸",
                    description = "가족 객실",
                    maxOccupancy = 4,
                    basePrice = Money.won(180_000)
                )
            )
            every {
                inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween("prop-1", "rt-1", checkIn, checkOut.minusDays(1))
            } returns listOf(
                inventory("prop-1", "rt-1", checkIn, totalCount = 2, reservedCount = 0),
                inventory("prop-1", "rt-1", checkIn.plusDays(1), totalCount = 2, reservedCount = 0)
            )
            every {
                inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween("prop-2", "rt-2", checkIn, checkOut.minusDays(1))
            } returns listOf(
                inventory("prop-2", "rt-2", checkIn, totalCount = 2, reservedCount = 2),
                inventory("prop-2", "rt-2", checkIn.plusDays(1), totalCount = 2, reservedCount = 0)
            )

            val result = service.searchProperties(PropertySearchCriteria(checkIn = checkIn, checkOut = checkOut, guests = 2))

            then("같은 객실 타입이 인원과 전체 숙박일 재고 조건을 만족하는 숙소만 반환된다") {
                result.map { it.id } shouldContainExactly listOf("prop-1")
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
                result.timezone shouldBe "Asia/Seoul"
                result.currency shouldBe "KRW"
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

    // -- 공개 예약 Offer 조회 --

    given("공개 예약 Offer 조회 시") {
        val checkIn = LocalDate.of(2026, 4, 10)
        val checkOut = LocalDate.of(2026, 4, 12)
        val deluxe = activeRoomType("rt-1", "prop-1")
        val suite = RoomType.create(
            id = "rt-2",
            propertyId = "prop-1",
            name = "패밀리 스위트",
            description = "가족 여행에 적합한 스위트",
            maxOccupancy = 4,
            basePrice = Money.won(180_000),
            amenities = listOf("WiFi", "욕조", "테라스")
        )

        `when`("숙박 기간의 최저 가용 수량과 요금을 객실별로 집계하면") {
            every { propertyRepository.findById("prop-1") } returns activeProperty()
            every { roomTypeRepository.findByPropertyId("prop-1") } returns listOf(deluxe, suite)
            every {
                inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                    "prop-1", "rt-1", checkIn, checkOut.minusDays(1)
                )
            } returns listOf(
                inventory("prop-1", "rt-1", checkIn, totalCount = 5, reservedCount = 4),
                inventory("prop-1", "rt-1", checkIn.plusDays(1), totalCount = 5, reservedCount = 2)
            )
            every {
                inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                    "prop-1", "rt-2", checkIn, checkOut.minusDays(1)
                )
            } returns listOf(
                inventory("prop-1", "rt-2", checkIn, totalCount = 2, reservedCount = 0),
                inventory("prop-1", "rt-2", checkIn.plusDays(1), totalCount = 2, reservedCount = 0)
            )
            every {
                ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
                    "prop-1", "rt-1", RatePlanStatus.ACTIVE
                )
            } returns emptyList()
            every {
                ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
                    "prop-1", "rt-2", RatePlanStatus.ACTIVE
                )
            } returns emptyList()

            val result = service.getReservationOffers("prop-1", checkIn, checkOut, 3)

            then("객실별 예약 제안과 인원 적합 여부를 반환한다") {
                result shouldHaveSize 2
                result.map { it.roomType.id } shouldContainExactly listOf("rt-1", "rt-2")
                result[0].availableCount shouldBe 1
                result[0].fitsGuests shouldBe false
                result[0].rateQuote shouldBe Money.won(200_000)
                result[1].availableCount shouldBe 2
                result[1].fitsGuests shouldBe true
                result[1].rateQuote shouldBe Money.won(360_000)
            }
        }

        `when`("숙박 기간 중 하루라도 재고가 없거나 누락되면") {
            every { propertyRepository.findById("prop-1") } returns activeProperty()
            every { roomTypeRepository.findByPropertyId("prop-1") } returns listOf(deluxe, suite)
            every {
                inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                    "prop-1", "rt-1", checkIn, checkOut.minusDays(1)
                )
            } returns listOf(
                inventory("prop-1", "rt-1", checkIn, totalCount = 1, reservedCount = 0)
            )
            every {
                inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                    "prop-1", "rt-2", checkIn, checkOut.minusDays(1)
                )
            } returns listOf(
                inventory("prop-1", "rt-2", checkIn, totalCount = 1, reservedCount = 0),
                inventory("prop-1", "rt-2", checkIn.plusDays(1), totalCount = 1, reservedCount = 1)
            )
            every {
                ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
                    "prop-1", "rt-1", RatePlanStatus.ACTIVE
                )
            } returns emptyList()
            every {
                ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
                    "prop-1", "rt-2", RatePlanStatus.ACTIVE
                )
            } returns emptyList()

            val result = service.getReservationOffers("prop-1", checkIn, checkOut, 2)

            then("해당 객실 타입의 가용 수량은 0으로 반환된다") {
                result shouldHaveSize 2
                result.map { it.availableCount } shouldContainExactly listOf(0, 0)
            }
        }
    }
})
