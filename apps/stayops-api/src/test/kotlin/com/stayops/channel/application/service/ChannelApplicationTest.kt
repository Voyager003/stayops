package com.stayops.channel.application.service

import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.service.ChannelInventoryQueryAdapter
import com.stayops.channel.domain.service.ExternalInventorySnapshot
import com.stayops.channel.domain.service.MockOtaRandomBookingResult
import com.stayops.channel.domain.service.MockOtaSimulationPort
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ChannelApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val roomTypeRepository = mockk<RoomTypeRepository>()
    val roomInventoryRepository = mockk<RoomInventoryRepository>()
    val channelSyncApplication = mockk<ChannelSyncApplication>(relaxed = true)
    val inventoryQueryAdapter = mockk<ChannelInventoryQueryAdapter>()
    val mockOtaSimulationPort = mockk<MockOtaSimulationPort>()
    val fixedInstant = Instant.parse("2026-04-08T10:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("Asia/Seoul"))
    val idGenerator = object : IdGenerator {
        override fun generate() = "test-id"
    }

    val sut = ChannelApplication(
        channelRepository = channelRepository,
        roomTypeRepository = roomTypeRepository,
        roomInventoryRepository = roomInventoryRepository,
        channelSyncApplication = channelSyncApplication,
        inventoryQueryAdapter = inventoryQueryAdapter,
        mockOtaSimulationPort = mockOtaSimulationPort,
        clock = fixedClock,
        idGenerator = idGenerator,
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

    // -- createOtaChannel --

    given("OTA 채널 생성 시") {
        `when`("유효한 요청이면") {
            then("채널이 저장되고 반환된다") {
                clearAllMocks()
                every { channelRepository.save(any()) } answers { firstArg() }
                every { roomTypeRepository.findByPropertyId("prop-1") } returns emptyList()

                val result = sut.createOtaChannel(
                    propertyId = "prop-1",
                    code = "AGODA",
                    name = "Agoda",
                    commissionRate = BigDecimal("0.15")
                )

                result.code shouldBe "AGODA"
                result.type shouldBe ChannelType.OTA
                verify { channelRepository.save(any()) }
            }
        }
    }

    // -- findChannel --

    given("채널 조회 시") {
        `when`("존재하지 않는 ID이면") {
            then("NotFoundException이 발생한다") {
                clearAllMocks()
                every { channelRepository.findById("not-exist") } returns null

                shouldThrow<NotFoundException> {
                    sut.findChannel("prop-1", "not-exist")
                }
            }
        }
    }

    given("채널 조회 시") {
        `when`("다른 숙소의 channelId로 조회하면") {
            then("NotFoundException이 발생한다 (테넌트 격리)") {
                clearAllMocks()
                every { channelRepository.findById("ch-1") } returns otaChannel()

                shouldThrow<NotFoundException> {
                    sut.findChannel("other-property", "ch-1")
                }
            }
        }
    }

    // -- activateChannel --

    given("비활성 채널 활성화 시") {
        `when`("activate 호출하면") {
            then("ACTIVE 상태로 변경된다") {
                clearAllMocks()
                val channel = otaChannel().deactivate()
                every { channelRepository.findById("ch-1") } returns channel
                every { channelRepository.save(any()) } answers { firstArg() }

                val result = sut.activateChannel("prop-1", "ch-1")

                result.status shouldBe ChannelStatus.ACTIVE
            }
        }
    }

    // -- compareInventory --

    fun pmsInventory(date: LocalDate, totalCount: Int = 5, reservedCount: Int = 0) =
        RoomInventory.reconstitute(
            id = "inv-$date",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            date = date,
            totalCount = totalCount,
            reservedCount = reservedCount,
            blockedCount = 0,
            version = 0L,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )

    given("재고 비교 시") {
        val startDate = LocalDate.of(2026, 5, 1)
        val endDate = LocalDate.of(2026, 5, 2)

        `when`("PMS와 OTA 재고가 일치하면") {
            then("isSynced가 true인 항목을 반환한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-1") } returns otaChannel()
                every {
                    inventoryQueryAdapter.fetchInventory("https://mock-ota/ari", "rt-1", startDate, endDate)
                } returns listOf(
                    ExternalInventorySnapshot("rt-1", startDate, 5),
                    ExternalInventorySnapshot("rt-1", endDate, 5)
                )
                every {
                    roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                        "prop-1", "rt-1", startDate, endDate
                    )
                } returns listOf(
                    pmsInventory(startDate, totalCount = 5),
                    pmsInventory(endDate, totalCount = 5)
                )

                val result = sut.compareInventory("prop-1", "ch-1", "rt-1", startDate, endDate)

                result.channelCode shouldBe "AGODA"
                result.items.size shouldBe 2
                result.items.all { it.isSynced } shouldBe true
            }
        }

        `when`("PMS와 OTA 재고가 다르면") {
            then("isSynced가 false인 항목을 반환한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-1") } returns otaChannel()
                every {
                    inventoryQueryAdapter.fetchInventory("https://mock-ota/ari", "rt-1", startDate, endDate)
                } returns listOf(ExternalInventorySnapshot("rt-1", startDate, 3))
                every {
                    roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                        "prop-1", "rt-1", startDate, endDate
                    )
                } returns listOf(pmsInventory(startDate, totalCount = 5))

                val result = sut.compareInventory("prop-1", "ch-1", "rt-1", startDate, endDate)

                result.items.size shouldBe 1
                result.items[0].pmsAvailableCount shouldBe 5
                result.items[0].otaAvailableCount shouldBe 3
                result.items[0].isSynced shouldBe false
            }
        }

        `when`("DIRECT 채널(connectionInfo 없음)에 대해 조회하면") {
            then("BusinessException이 발생한다") {
                clearAllMocks()
                val directChannel = Channel.createDirect(id = "ch-0", propertyId = "prop-1")
                every { channelRepository.findById("ch-0") } returns directChannel

                val ex = shouldThrow<BusinessException> {
                    sut.compareInventory("prop-1", "ch-0", "rt-1", startDate, endDate)
                }
                ex.code shouldBe "DIRECT_CHANNEL_NOT_SUPPORTED"
            }
        }
    }

    given("OTA 랜덤 예약 시뮬레이션 시") {
        `when`("OTA 채널이면") {
            then("환경 설정 endpoint로 Mock OTA를 호출한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-1") } returns otaChannel()
                every {
                    mockOtaSimulationPort.simulateRandomBooking("https://mock-ota/ari", "prop-1", "AGODA")
                } returns MockOtaRandomBookingResult(
                    status = "sent",
                    bookingId = "booking-1",
                    roomTypeId = "rt-1",
                    date = "2026-05-01",
                    guestName = "김민수"
                )

                val result = sut.simulateRandomBooking("prop-1", "ch-1")

                result.bookingId shouldBe "booking-1"
                result.guestName shouldBe "김민수"
                verify {
                    mockOtaSimulationPort.simulateRandomBooking("https://mock-ota/ari", "prop-1", "AGODA")
                }
            }
        }

        `when`("DIRECT 채널이면") {
            then("BusinessException이 발생한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-0") } returns Channel.createDirect(id = "ch-0", propertyId = "prop-1")

                val ex = shouldThrow<BusinessException> {
                    sut.simulateRandomBooking("prop-1", "ch-0")
                }

                ex.code shouldBe "DIRECT_CHANNEL_NOT_SUPPORTED"
            }
        }
    }

})
