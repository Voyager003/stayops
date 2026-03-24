package com.stayops.channel.infrastructure.persistence

import com.stayops.IntegrationTestSupport
import com.stayops.channel.domain.model.SyncTask
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.model.SyncTaskType
import com.stayops.channel.domain.repository.SyncTaskRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class MongoSyncTaskRepositoryTest @Autowired constructor(
    private val syncTaskRepository: SyncTaskRepository,
    private val mongoDataRepository: SyncTaskMongoDataRepository
) : IntegrationTestSupport() {

    @BeforeEach
    fun setUp() {
        mongoDataRepository.deleteAll()
    }

    private fun newTask(
        id: String = "task-1",
        propertyId: String = "prop-1",
        channelCode: String = "AGODA"
    ) = SyncTask.create(
        id = id,
        propertyId = propertyId,
        channelCode = channelCode,
        type = SyncTaskType.AVAILABILITY_UPDATE,
        payload = mapOf("roomTypeId" to "rt-1", "date" to "2026-03-20", "availableCount" to 3)
    )

    @Nested
    inner class `save 및 findById` {
        @Test
        fun `저장 후 동일한 도메인 객체를 조회한다`() {
            val task = newTask()
            syncTaskRepository.save(task)

            val found = syncTaskRepository.findById("task-1")

            assertThat(found).isNotNull
            assertThat(found!!.channelCode).isEqualTo("AGODA")
            assertThat(found.status).isEqualTo(SyncTaskStatus.PENDING)
            assertThat(found.payload["roomTypeId"]).isEqualTo("rt-1")
        }
    }

    @Nested
    inner class `findPendingTasksReadyForProcessing` {
        @Test
        fun `nextRetryAt이 null인 PENDING 태스크를 반환한다`() {
            syncTaskRepository.save(newTask(id = "task-1"))

            val tasks = syncTaskRepository.findPendingTasksReadyForProcessing(Instant.now())

            assertThat(tasks).hasSize(1)
        }

        @Test
        fun `nextRetryAt이 과거인 PENDING 태스크를 반환한다`() {
            val task = newTask(id = "task-2")
                .startProcessing()
                .fail("error")
            syncTaskRepository.save(task)

            val farFuture = Instant.now().plusSeconds(600)
            val tasks = syncTaskRepository.findPendingTasksReadyForProcessing(farFuture)

            assertThat(tasks).hasSize(1)
        }

        @Test
        fun `nextRetryAt이 미래인 PENDING 태스크는 반환하지 않는다`() {
            val task = newTask(id = "task-3")
                .startProcessing()
                .fail("error")
            syncTaskRepository.save(task)

            val past = Instant.now().minusSeconds(600)
            val tasks = syncTaskRepository.findPendingTasksReadyForProcessing(past)

            assertThat(tasks).isEmpty()
        }

        @Test
        fun `COMPLETED 태스크는 반환하지 않는다`() {
            val task = newTask(id = "task-4").startProcessing().complete()
            syncTaskRepository.save(task)

            val tasks = syncTaskRepository.findPendingTasksReadyForProcessing(Instant.now())

            assertThat(tasks).isEmpty()
        }
    }

    @Nested
    inner class `countByPropertyIdAndChannelCodeGroupByStatus` {
        @Test
        fun `상태별 개수를 집계한다`() {
            syncTaskRepository.save(newTask(id = "t-1"))
            syncTaskRepository.save(newTask(id = "t-2"))
            syncTaskRepository.save(newTask(id = "t-3").startProcessing().complete())

            val counts = syncTaskRepository.countByPropertyIdAndChannelCodeGroupByStatus("prop-1", "AGODA")

            assertThat(counts[SyncTaskStatus.PENDING]).isEqualTo(2L)
            assertThat(counts[SyncTaskStatus.COMPLETED]).isEqualTo(1L)
        }
    }
}
