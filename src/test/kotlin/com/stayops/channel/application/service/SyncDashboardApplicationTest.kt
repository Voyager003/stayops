package com.stayops.channel.application.service

import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.SyncTaskRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal

class SyncDashboardApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val syncTaskRepository = mockk<SyncTaskRepository>()

    val sut = SyncDashboardApplication(
        channelRepository = channelRepository,
        syncTaskRepository = syncTaskRepository
    )

    fun otaChannel(code: String, name: String) = Channel.createOta(
        id = "ch-$code",
        propertyId = "prop-1",
        code = code,
        name = name,
        commissionRate = BigDecimal("0.15"),
        connectionInfo = ChannelConnectionInfo(
            apiEndpoint = "https://mock-ota/ari",
            apiKey = null,
            apiSecret = null,
            webhookSecret = "secret"
        )
    )

    given("OTA 채널 2개와 SyncTask가 있을 때") {
        `when`("대시보드를 조회하면") {
            then("채널별 상태 집계와 전체 요약을 반환한다") {
                clearAllMocks()
                every { channelRepository.findByPropertyId("prop-1") } returns listOf(
                    Channel.createDirect("ch-0", "prop-1"),
                    otaChannel("AGODA", "Agoda"),
                    otaChannel("BOOKING", "Booking.com")
                )
                every { syncTaskRepository.countByPropertyIdAndChannelCodeGroupByStatus("prop-1", "AGODA") } returns
                        mapOf(SyncTaskStatus.PENDING to 2L, SyncTaskStatus.COMPLETED to 10L, SyncTaskStatus.FAILED to 1L)
                every { syncTaskRepository.countByPropertyIdAndChannelCodeGroupByStatus("prop-1", "BOOKING") } returns
                        mapOf(SyncTaskStatus.COMPLETED to 5L)

                val dashboard = sut.getDashboard("prop-1")

                dashboard.channels.size shouldBe 2
                dashboard.channels[0].channelCode shouldBe "AGODA"
                dashboard.channels[0].pendingCount shouldBe 2L
                dashboard.channels[0].failedCount shouldBe 1L
                dashboard.channels[1].channelCode shouldBe "BOOKING"
                dashboard.channels[1].completedCount shouldBe 5L
                dashboard.channels[1].failedCount shouldBe 0L

                dashboard.summary.totalPending shouldBe 2L
                dashboard.summary.totalCompleted shouldBe 15L
                dashboard.summary.totalFailed shouldBe 1L
            }
        }
    }
})
