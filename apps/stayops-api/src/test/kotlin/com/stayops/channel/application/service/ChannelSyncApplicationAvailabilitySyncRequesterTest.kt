package com.stayops.channel.application.service

import com.stayops.inventory.application.required.AvailabilitySyncRequester
import com.stayops.channel.application.required.ChannelAvailabilityPublisherProvider
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.SyncTaskRepository
import com.stayops.shared.domain.IdGenerator
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ChannelSyncApplicationAvailabilitySyncRequesterTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val syncTaskRepository = mockk<SyncTaskRepository>()
    val publisherProvider = mockk<ChannelAvailabilityPublisherProvider>(relaxed = true)
    val sut: AvailabilitySyncRequester = ChannelSyncApplication(
        channelRepository = channelRepository,
        syncTaskRepository = syncTaskRepository,
        publisherProvider = publisherProvider,
        clock = Clock.fixed(Instant.parse("2026-04-08T10:00:00Z"), ZoneOffset.UTC),
        idGenerator = object : IdGenerator {
            override fun generate() = "task-1"
        }
    )

    fun otaChannel() = Channel.createOta(
        id = "ch-1",
        propertyId = "prop-1",
        code = "AGODA",
        name = "AGODA",
        commissionRate = BigDecimal("0.15"),
        apiEndpoint = "https://mock-ota"
    )

    given("가용 재고 동기화 요청이 들어오면") {
        `when`("동기화 task 생성을 요청하면") {
            then("ChannelSyncApplication이 AvailabilitySyncRequester 계약으로 SyncTask를 생성한다") {
                every { channelRepository.findByPropertyIdAndStatus("prop-1", ChannelStatus.ACTIVE) } returns listOf(otaChannel())
                every { syncTaskRepository.save(any()) } answers { firstArg() }

                sut.requestAvailabilitySync("prop-1", "rt-1", LocalDate.of(2026, 4, 12), 3)

                verify(exactly = 1) { syncTaskRepository.save(any()) }
            }
        }
    }
})
