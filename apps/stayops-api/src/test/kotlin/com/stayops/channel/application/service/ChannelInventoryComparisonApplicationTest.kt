package com.stayops.channel.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.application.required.ChannelInventorySnapshotReader
import com.stayops.channel.application.required.ExternalInventorySnapshot
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.shared.exception.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ChannelInventoryComparisonApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val roomInventoryRepository = mockk<RoomInventoryRepository>()
    val inventorySnapshotReader = mockk<ChannelInventorySnapshotReader>()

    val sut = ChannelInventoryComparisonApplication(
        channelRepository = channelRepository,
        roomInventoryRepository = roomInventoryRepository,
        inventorySnapshotReader = inventorySnapshotReader
    )

    val fixedInstant = Instant.parse("2026-04-08T10:00:00Z")
    val startDate = LocalDate.of(2026, 5, 1)
    val endDate = LocalDate.of(2026, 5, 2)

    fun otaChannel(id: String = "ch-1", code: String = "AGODA") = Channel.createOta(
        id = id,
        propertyId = "prop-1",
        code = code,
        name = code,
        commissionRate = BigDecimal("0.15"),
        apiEndpoint = "https://mock-ota/ari"
    )

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

    given("채널 재고 비교는") {
        `when`("PMS와 OTA 재고가 일치하면") {
            then("동기화된 항목을 반환한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-1") } returns otaChannel()
                every {
                    inventorySnapshotReader.fetchInventory("https://mock-ota/ari", "prop-1", "AGODA", "rt-1", startDate, endDate)
                } returns listOf(
                    ExternalInventorySnapshot("rt-1", startDate, 5),
                    ExternalInventorySnapshot("rt-1", endDate, 5)
                )
                every {
                    roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                        "prop-1",
                        "rt-1",
                        startDate,
                        endDate
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
            then("차이가 있는 항목을 반환한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-1") } returns otaChannel()
                every {
                    inventorySnapshotReader.fetchInventory("https://mock-ota/ari", "prop-1", "AGODA", "rt-1", startDate, endDate)
                } returns listOf(ExternalInventorySnapshot("rt-1", startDate, 3))
                every {
                    roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                        "prop-1",
                        "rt-1",
                        startDate,
                        endDate
                    )
                } returns listOf(pmsInventory(startDate, totalCount = 5))

                val result = sut.compareInventory("prop-1", "ch-1", "rt-1", startDate, endDate)

                result.items.size shouldBe 1
                result.items[0].pmsAvailableCount shouldBe 5
                result.items[0].otaAvailableCount shouldBe 3
                result.items[0].isSynced shouldBe false
            }
        }

        `when`("DIRECT 채널에 대해 비교하면") {
            then("OTA 채널만 지원한다고 거부한다") {
                clearAllMocks()
                every { channelRepository.findById("ch-0") } returns Channel.createDirect(id = "ch-0", propertyId = "prop-1")

                val exception = shouldThrow<BusinessException> {
                    sut.compareInventory("prop-1", "ch-0", "rt-1", startDate, endDate)
                }

                exception.code shouldBe "DIRECT_CHANNEL_NOT_SUPPORTED"
            }
        }
    }
})
