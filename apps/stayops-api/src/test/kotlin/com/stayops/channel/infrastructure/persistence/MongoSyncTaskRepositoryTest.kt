package com.stayops.channel.infrastructure.persistence

import com.stayops.TestcontainersConfiguration
import com.stayops.channel.domain.model.SyncTask
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.model.SyncTaskType
import com.stayops.channel.domain.repository.SyncTaskRepository
import com.stayops.shared.config.FixedTestClockConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Clock
import java.time.Instant

@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class MongoSyncTaskRepositoryTest @Autowired constructor(
    private val syncTaskRepository: SyncTaskRepository,
    private val mongoDataRepository: SyncTaskMongoDataRepository,
    private val clock: Clock
) {

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
        payload = mapOf("roomTypeId" to "rt-1", "date" to "2026-03-20", "availableCount" to 3),
        now = clock.instant()
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
    inner class `claimReadyForProcessing` {
        @Test
        fun `ready 태스크를 IN_PROGRESS로 원자적으로 claim한다`() {
            val now = Instant.now(clock)
            syncTaskRepository.save(newTask(id = "task-claim"))

            val claimed = syncTaskRepository.claimReadyForProcessing(
                workerId = "worker-a",
                now = now,
                lockedUntil = now.plusSeconds(60)
            )

            assertThat(claimed).isNotNull
            assertThat(claimed!!.id).isEqualTo("task-claim")
            assertThat(claimed.status).isEqualTo(SyncTaskStatus.IN_PROGRESS)
            assertThat(claimed.lockedBy).isEqualTo("worker-a")
            assertThat(claimed.lockedUntil).isEqualTo(now.plusSeconds(60))

            val completed = syncTaskRepository.save(claimed.complete(now.plusSeconds(1)))
            assertThat(completed.status).isEqualTo(SyncTaskStatus.COMPLETED)
        }

        @Test
        fun `이미 claim된 태스크는 lease 만료 전 다른 worker가 다시 claim하지 못한다`() {
            val now = Instant.now(clock)
            syncTaskRepository.save(newTask(id = "task-once"))

            val first = syncTaskRepository.claimReadyForProcessing(
                workerId = "worker-a",
                now = now,
                lockedUntil = now.plusSeconds(60)
            )
            val second = syncTaskRepository.claimReadyForProcessing(
                workerId = "worker-b",
                now = now.plusSeconds(1),
                lockedUntil = now.plusSeconds(61)
            )

            assertThat(first).isNotNull
            assertThat(second).isNull()
        }

        @Test
        fun `lease가 만료된 IN_PROGRESS 태스크는 다른 worker가 다시 claim할 수 있다`() {
            val now = Instant.now(clock)
            val expired = newTask(id = "task-expired")
                .startProcessing("worker-a", now.minusSeconds(1), now.minusSeconds(61))
            syncTaskRepository.save(expired)

            val claimed = syncTaskRepository.claimReadyForProcessing(
                workerId = "worker-b",
                now = now,
                lockedUntil = now.plusSeconds(60)
            )

            assertThat(claimed).isNotNull
            assertThat(claimed!!.id).isEqualTo("task-expired")
            assertThat(claimed.lockedBy).isEqualTo("worker-b")
            assertThat(claimed.status).isEqualTo(SyncTaskStatus.IN_PROGRESS)
        }
    }

    @Nested
    inner class `findPendingTasksReadyForProcessing` {
        @Test
        fun `nextRetryAt이 null인 PENDING 태스크를 반환한다`() {
            syncTaskRepository.save(newTask(id = "task-1"))

            val tasks = syncTaskRepository.findPendingTasksReadyForProcessing(Instant.now(clock))

            assertThat(tasks).hasSize(1)
        }

        @Test
        fun `nextRetryAt이 과거인 PENDING 태스크를 반환한다`() {
            val task = newTask(id = "task-2")
                .startProcessing(clock.instant())
                .fail("error", clock.instant())
            syncTaskRepository.save(task)

            val farFuture = Instant.now(clock).plusSeconds(600)
            val tasks = syncTaskRepository.findPendingTasksReadyForProcessing(farFuture)

            assertThat(tasks).hasSize(1)
        }

        @Test
        fun `nextRetryAt이 미래인 PENDING 태스크는 반환하지 않는다`() {
            val task = newTask(id = "task-3")
                .startProcessing(clock.instant())
                .fail("error", clock.instant())
            syncTaskRepository.save(task)

            val past = Instant.now(clock).minusSeconds(600)
            val tasks = syncTaskRepository.findPendingTasksReadyForProcessing(past)

            assertThat(tasks).isEmpty()
        }

        @Test
        fun `COMPLETED 태스크는 반환하지 않는다`() {
            val task = newTask(id = "task-4")
                .startProcessing(clock.instant())
                .complete(clock.instant())
            syncTaskRepository.save(task)

            val tasks = syncTaskRepository.findPendingTasksReadyForProcessing(Instant.now(clock))

            assertThat(tasks).isEmpty()
        }

        @Test
        fun `lease가 만료된 IN_PROGRESS 태스크를 반환한다`() {
            val now = Instant.now(clock)
            val task = newTask(id = "task-5")
                .startProcessing("worker-a", now.plusSeconds(60), now)
            syncTaskRepository.save(task)

            val tasks = syncTaskRepository.findPendingTasksReadyForProcessing(now.plusSeconds(61))

            assertThat(tasks).extracting("id").containsExactly("task-5")
        }
    }

    @Nested
    inner class `countByPropertyIdAndChannelCodeGroupByStatus` {
        @Test
        fun `상태별 개수를 집계한다`() {
            syncTaskRepository.save(newTask(id = "t-1"))
            syncTaskRepository.save(newTask(id = "t-2"))
            syncTaskRepository.save(
                newTask(id = "t-3")
                    .startProcessing(clock.instant())
                    .complete(clock.instant())
            )

            val counts = syncTaskRepository.countByPropertyIdAndChannelCodeGroupByStatus("prop-1", "AGODA")

            assertThat(counts[SyncTaskStatus.PENDING]).isEqualTo(2L)
            assertThat(counts[SyncTaskStatus.COMPLETED]).isEqualTo(1L)
        }
    }
}
