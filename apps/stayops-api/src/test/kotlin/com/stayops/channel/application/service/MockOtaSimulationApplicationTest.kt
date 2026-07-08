package com.stayops.channel.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.application.required.MockOtaRandomBookingResult
import com.stayops.channel.application.required.MockOtaBookingSimulator
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MockOtaSimulationApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val mockOtaBookingSimulator = mockk<MockOtaBookingSimulator>()
    val roomTypeRepository = mockk<RoomTypeRepository>()
    val roomInventoryRepository = mockk<RoomInventoryRepository>()
    val clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("UTC"))

    val sut = MockOtaSimulationApplication(
        channelRepository = channelRepository,
        mockOtaBookingSimulator = mockOtaBookingSimulator,
        roomTypeRepository = roomTypeRepository,
        roomInventoryRepository = roomInventoryRepository,
        clock = clock,
        otaEndpoint = "https://mock-ota/ari"
    )

    fun otaChannel(id: String = "ch-1", code: String = "AGODA") = Channel.createOta(
        id = id,
        propertyId = "prop-1",
        code = code,
        name = code,
        commissionRate = BigDecimal("0.15"),
        apiEndpoint = "https://mock-ota/ari"
    )

    fun roomType(id: String) = RoomType.create(
        id = id,
        propertyId = "prop-1",
        name = id,
        description = "test",
        maxOccupancy = 2,
        basePrice = Money.won(100_000)
    )

    fun inventory(roomTypeId: String, date: LocalDate, availableCount: Int): RoomInventory {
        var inventory = RoomInventory.create(
            id = "$roomTypeId-$date",
            propertyId = "prop-1",
            roomTypeId = roomTypeId,
            date = date,
            totalCount = 5
        )
        if (availableCount > 0) {
            inventory = inventory.unblock(availableCount)
        }
        return inventory
    }

    given("Mock OTA 예약 시뮬레이션은") {
        `when`("OTA 채널이면") {
            then("PMS 가용 재고 중 가장 이른 날짜와 객실타입으로 Mock OTA를 호출한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-1") } returns otaChannel()
                every { roomTypeRepository.findByPropertyId("prop-1") } returns listOf(roomType("rt-b"), roomType("rt-a"))
                every {
                    roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                        "prop-1",
                        "rt-b",
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 6, 30)
                    )
                } returns listOf(inventory("rt-b", LocalDate.of(2026, 4, 5), 2))
                every {
                    roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                        "prop-1",
                        "rt-a",
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 6, 30)
                    )
                } returns listOf(inventory("rt-a", LocalDate.of(2026, 4, 3), 1))
                every {
                    mockOtaBookingSimulator.simulateInventoryBooking(
                        endpoint = "https://mock-ota/ari",
                        propertyId = "prop-1",
                        channelCode = "AGODA",
                        roomTypeCode = "rt-a",
                        date = LocalDate.of(2026, 4, 3)
                    )
                } returns MockOtaRandomBookingResult(
                    status = "sent",
                    bookingId = "booking-1",
                    roomTypeId = "rt-a",
                    date = "2026-04-03",
                    guestName = "김민수"
                )

                val result = sut.simulateRandomBooking("prop-1", "ch-1")

                result.bookingId shouldBe "booking-1"
                result.guestName shouldBe "김민수"
                verify {
                    mockOtaBookingSimulator.simulateInventoryBooking(
                        endpoint = "https://mock-ota/ari",
                        propertyId = "prop-1",
                        channelCode = "AGODA",
                        roomTypeCode = "rt-a",
                        date = LocalDate.of(2026, 4, 3)
                    )
                }
            }

            then("PMS 가용 재고가 없으면 Mock OTA를 호출하지 않고 거부한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-1") } returns otaChannel()
                every { roomTypeRepository.findByPropertyId("prop-1") } returns listOf(roomType("rt-a"))
                every {
                    roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                        "prop-1",
                        "rt-a",
                        LocalDate.of(2026, 4, 1),
                        LocalDate.of(2026, 6, 30)
                    )
                } returns listOf(inventory("rt-a", LocalDate.of(2026, 4, 3), 0))

                val exception = shouldThrow<BusinessException> {
                    sut.simulateRandomBooking("prop-1", "ch-1")
                }

                exception.code shouldBe "MOCK_OTA_SIMULATION_NO_AVAILABLE_INVENTORY"
                verify(exactly = 0) {
                    mockOtaBookingSimulator.simulateInventoryBooking(any(), any(), any(), any(), any())
                }
            }
        }

        `when`("DIRECT 채널이면") {
            then("OTA 채널만 지원한다고 거부한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-0") } returns Channel.createDirect(id = "ch-0", propertyId = "prop-1")

                val exception = shouldThrow<BusinessException> {
                    sut.simulateRandomBooking("prop-1", "ch-0")
                }

                exception.code shouldBe "DIRECT_CHANNEL_NOT_SUPPORTED"
            }
        }
    }
})
