package com.stayops.channel.application.service

import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.SyncTaskRepository
import com.stayops.channel.domain.service.SyncResult
import com.stayops.channel.infrastructure.external.ChannelAdapterRegistry
import com.stayops.channel.infrastructure.external.HttpChannelSyncAdapter
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.*
import java.math.BigDecimal
import java.time.LocalDate

class ChannelSyncApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val mappingRepository = mockk<ChannelMappingRepository>()
    val syncTaskRepository = mockk<SyncTaskRepository>()
    val httpAdapter = mockk<HttpChannelSyncAdapter>()
    val adapterRegistry = ChannelAdapterRegistry(httpAdapter)

    val sut = ChannelSyncApplication(
        channelRepository = channelRepository,
        channelMappingRepository = mappingRepository,
        syncTaskRepository = syncTaskRepository,
        adapterRegistry = adapterRegistry
    )

    fun otaChannel(code: String = "AGODA") = Channel.createOta(
        id = "ch-1",
        propertyId = "prop-1",
        code = code,
        name = code,
        commissionRate = BigDecimal("0.15"),
        connectionInfo = ChannelConnectionInfo(
            apiEndpoint = "https://mock-ota:8081/api/v1/ari/availability",
            apiKey = "test-key",
            apiSecret = null,
            webhookSecret = "secret"
        )
    )

    fun directChannel() = Channel.createDirect(id = "ch-0", propertyId = "prop-1")

    fun sampleTask() = SyncTask.create(
        id = "task-1",
        propertyId = "prop-1",
        channelCode = "AGODA",
        type = SyncTaskType.AVAILABILITY_UPDATE,
        payload = mapOf("roomTypeId" to "rt-1", "date" to "2026-03-20", "availableCount" to 5)
    )

    fun sampleMapping() = ChannelMapping.create("map-1", "prop-1", "AGODA")
        .addMapping(MappingEntry("rt-1", "AGD-DELUXE", MappingType.ROOM_TYPE))

    // -- createAvailabilitySyncTasks --

    given("활성 OTA 채널이 2개인 숙소에서") {
        `when`("재고 변경 시 SyncTask를 생성하면") {
            then("OTA 채널 수만큼 SyncTask가 생성된다 (DIRECT 제외)") {
                clearAllMocks()
                every { channelRepository.findByPropertyIdAndStatus("prop-1", ChannelStatus.ACTIVE) } returns
                        listOf(directChannel(), otaChannel("AGODA"), otaChannel("BOOKING"))
                every { syncTaskRepository.save(any()) } answers { firstArg() }

                sut.createAvailabilitySyncTasks("prop-1", "rt-1", LocalDate.of(2026, 3, 20), 5)

                verify(exactly = 2) { syncTaskRepository.save(any()) }
            }
        }
    }

    // -- processPendingTasks: 성공 --

    given("PENDING SyncTask가 있고 ARI push가 성공할 때") {
        `when`("processPendingTasks() 호출하면") {
            then("태스크가 COMPLETED로 저장되고 외부 코드로 변환되어 push된다") {
                clearAllMocks()
                every { syncTaskRepository.findPendingTasksReadyForProcessing(any()) } returns listOf(sampleTask())
                every { syncTaskRepository.save(any()) } answers { firstArg() }
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel()
                every { mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA") } returns sampleMapping()
                every { httpAdapter.pushAvailability(any(), any(), any(), any(), any()) } returns SyncResult(success = true)

                sut.processPendingTasks()

                verify {
                    syncTaskRepository.save(match { it.status == SyncTaskStatus.IN_PROGRESS })
                    syncTaskRepository.save(match { it.status == SyncTaskStatus.COMPLETED })
                }
                verify {
                    httpAdapter.pushAvailability(
                        endpoint = "https://mock-ota:8081/api/v1/ari/availability",
                        apiKey = "test-key",
                        externalRoomTypeCode = "AGD-DELUXE",
                        payload = any(),
                        idempotencyKey = any()
                    )
                }
            }
        }
    }

    // -- processPendingTasks: 실패 --

    given("PENDING SyncTask가 있고 ARI push가 실패할 때") {
        `when`("processPendingTasks() 호출하면") {
            then("태스크가 fail 처리된다") {
                clearAllMocks()
                every { syncTaskRepository.findPendingTasksReadyForProcessing(any()) } returns listOf(sampleTask())
                every { syncTaskRepository.save(any()) } answers { firstArg() }
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel()
                every { mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA") } returns sampleMapping()
                every { httpAdapter.pushAvailability(any(), any(), any(), any(), any()) } returns
                        SyncResult(success = false, errorMessage = "Connection timeout")

                sut.processPendingTasks()

                verify {
                    syncTaskRepository.save(match { it.status == SyncTaskStatus.IN_PROGRESS })
                    syncTaskRepository.save(match { it.lastError == "Connection timeout" })
                }
            }
        }
    }
})
