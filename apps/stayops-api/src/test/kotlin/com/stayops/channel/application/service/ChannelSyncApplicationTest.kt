package com.stayops.channel.application.service

import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.SyncTaskRepository
import com.stayops.channel.application.required.ChannelAvailabilityPublisherProvider
import com.stayops.channel.application.required.ChannelAvailabilityPublisher
import com.stayops.channel.application.required.SyncResult
import com.stayops.shared.domain.IdGenerator
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ChannelSyncApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val syncTaskRepository = mockk<SyncTaskRepository>()
    val publisherProvider = mockk<ChannelAvailabilityPublisherProvider>()
    val availabilityPublisher = mockk<ChannelAvailabilityPublisher>()
    val fixedClock = Clock.fixed(Instant.parse("2026-04-08T10:00:00Z"), ZoneId.of("Asia/Seoul"))
    val idGenerator = object : IdGenerator {
        override fun generate() = "task-1"
    }

    val sut = ChannelSyncApplication(
        channelRepository = channelRepository,
        syncTaskRepository = syncTaskRepository,
        publisherProvider = publisherProvider,
        clock = fixedClock,
        idGenerator = idGenerator
    )

    fun otaChannel(code: String = "AGODA") = Channel.createOta(
        id = "ch-1",
        propertyId = "prop-1",
        code = code,
        name = code,
        commissionRate = BigDecimal("0.15"),
        apiEndpoint = "https://mock-ota:8081/api/v1/ari/availability"
    )

    fun directChannel() = Channel.createDirect(id = "ch-0", propertyId = "prop-1")

    fun sampleTask() = SyncTask.create(
        id = "task-1",
        propertyId = "prop-1",
        channelCode = "AGODA",
        type = SyncTaskType.AVAILABILITY_UPDATE,
        payload = mapOf("roomTypeId" to "rt-1", "date" to "2026-03-20", "availableCount" to 5)
    )

    fun claimedTask(task: SyncTask = sampleTask(), workerId: String = "sync-task-worker") =
        task.startProcessing(workerId, fixedClock.instant().plusSeconds(60), fixedClock.instant())

    // -- requestAvailabilitySync --

    given("활성 OTA 채널이 2개인 숙소에서") {
        `when`("재고 변경 시 SyncTask를 생성하면") {
            then("OTA 채널 수만큼 SyncTask가 생성된다 (DIRECT 제외)") {
                clearAllMocks()
                every { channelRepository.findByPropertyIdAndStatus("prop-1", ChannelStatus.ACTIVE) } returns
                        listOf(directChannel(), otaChannel("AGODA"), otaChannel("BOOKING"))
                every { syncTaskRepository.save(any()) } answers { firstArg() }

                sut.requestAvailabilitySync("prop-1", "rt-1", LocalDate.of(2026, 3, 20), 5)

                verify(exactly = 2) { syncTaskRepository.save(any()) }
            }
        }
    }

    // -- processPendingTasks: 성공 --

    given("PENDING SyncTask가 있고 ARI push가 성공할 때") {
        `when`("processPendingTasks() 호출하면") {
            then("태스크가 COMPLETED로 저장되고 roomTypeId로 push된다") {
                clearAllMocks()
                every { syncTaskRepository.claimReadyForProcessing(any(), any(), any()) } returnsMany
                    listOf(claimedTask(), null)
                every { syncTaskRepository.save(any()) } answers { firstArg() }
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel()
                every { publisherProvider.getPublisher("AGODA") } returns availabilityPublisher
                every { availabilityPublisher.pushAvailability(any(), any(), any(), any(), any(), any(), any()) } returns SyncResult(success = true)

                sut.processPendingTasks()

                verify {
                    syncTaskRepository.save(match { it.status == SyncTaskStatus.COMPLETED })
                }
                verify {
                    availabilityPublisher.pushAvailability(
                        endpoint = "https://mock-ota:8081/api/v1/ari/availability",
                        apiKey = null,
                        propertyId = "prop-1",
                        channelCode = "AGODA",
                        externalRoomTypeCode = "rt-1",
                        payload = any(),
                        idempotencyKey = any()
                    )
                }
            }

            then("처리 시작 저장 후 반환된 최신 version으로 완료 저장한다") {
                clearAllMocks()
                val originalTask = sampleTask()
                val savedTasks = mutableListOf<SyncTask>()

                every { syncTaskRepository.claimReadyForProcessing(any(), any(), any()) } returnsMany
                    listOf(
                        SyncTask.reconstitute(
                            id = originalTask.id,
                            propertyId = originalTask.propertyId,
                            channelCode = originalTask.channelCode,
                            type = originalTask.type,
                            payload = originalTask.payload,
                            idempotencyKey = originalTask.idempotencyKey,
                            status = SyncTaskStatus.IN_PROGRESS,
                            retryCount = originalTask.retryCount,
                            maxRetries = originalTask.maxRetries,
                            nextRetryAt = originalTask.nextRetryAt,
                            lockedBy = "sync-task-worker",
                            lockedUntil = fixedClock.instant().plusSeconds(60),
                            lastError = originalTask.lastError,
                            version = 1L,
                            createdAt = originalTask.createdAt,
                            updatedAt = fixedClock.instant()
                        ),
                        null
                    )
                every { syncTaskRepository.save(any()) } answers {
                    val task = firstArg<SyncTask>()
                    savedTasks += task
                    task
                }
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel()
                every { publisherProvider.getPublisher("AGODA") } returns availabilityPublisher
                every { availabilityPublisher.pushAvailability(any(), any(), any(), any(), any(), any(), any()) } returns SyncResult(success = true)

                sut.processPendingTasks()

                val completed = savedTasks.single { it.status == SyncTaskStatus.COMPLETED }
                completed.version shouldBe 1L
            }
        }
    }

    // -- processPendingTasks: 실패 --

    given("PENDING SyncTask가 있고 ARI push가 실패할 때") {
        `when`("processPendingTasks() 호출하면") {
            then("태스크가 fail 처리된다") {
                clearAllMocks()
                every { syncTaskRepository.claimReadyForProcessing(any(), any(), any()) } returnsMany
                    listOf(claimedTask(), null)
                every { syncTaskRepository.save(any()) } answers { firstArg() }
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel()
                every { publisherProvider.getPublisher("AGODA") } returns availabilityPublisher
                every { availabilityPublisher.pushAvailability(any(), any(), any(), any(), any(), any(), any()) } returns
                        SyncResult(success = false, errorMessage = "Connection timeout")

                sut.processPendingTasks()

                verify {
                    syncTaskRepository.save(match { it.lastError == "Connection timeout" })
                }
            }
        }
    }

    // -- processPendingTasks: version 충돌 처리 --

    given("폴링 중 version 충돌이 발생하면") {
        `when`("ConflictException이 발생해도") {
            then("에러 없이 다음 태스크를 계속 처리한다") {
                clearAllMocks()
                val task1 = sampleTask()
                val task2 = SyncTask.create("task-2", "prop-1", "BOOKING", SyncTaskType.AVAILABILITY_UPDATE,
                    mapOf("roomTypeId" to "rt-1", "date" to "2026-03-20", "availableCount" to 3))

                every { syncTaskRepository.claimReadyForProcessing(any(), any(), any()) } returnsMany
                    listOf(claimedTask(task1), claimedTask(task2, "sync-task-worker"), null)
                every { syncTaskRepository.save(match { it.id == "task-1" }) } throws
                        com.stayops.shared.exception.ConflictException("SYNC_TASK_CONFLICT", "version conflict")
                every { syncTaskRepository.save(match { it.id == "task-2" }) } answers { firstArg() }
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel("AGODA")
                every { channelRepository.findByPropertyIdAndCode("prop-1", "BOOKING") } returns otaChannel("BOOKING")
                every { publisherProvider.getPublisher("AGODA") } returns availabilityPublisher
                every { publisherProvider.getPublisher("BOOKING") } returns availabilityPublisher
                every { availabilityPublisher.pushAvailability(any(), any(), any(), any(), any(), any(), any()) } returns SyncResult(success = true)

                sut.processPendingTasks()

                // task2는 정상 처리됨
                verify(exactly = 2) {
                    availabilityPublisher.pushAvailability(any(), any(), any(), any(), any(), any(), any())
                }
            }
        }
    }

    // -- retryTask: 테넌트 격리 --

    given("다른 숙소의 taskId로 재시도 요청 시") {
        `when`("propertyId가 일치하지 않으면") {
            then("NotFoundException이 발생한다") {
                clearAllMocks()
                val task = sampleTask() // propertyId = "prop-1"
                every { syncTaskRepository.findById("task-1") } returns task.startProcessing().fail("error")

                io.kotest.assertions.throwables.shouldThrow<com.stayops.shared.exception.NotFoundException> {
                    sut.retryTask("other-property", "task-1")
                }
            }
        }
    }
})
