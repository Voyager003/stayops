package com.stayops.shared.scheduler

import com.stayops.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Instant

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MongoSchedulerLockTest @Autowired constructor(
    private val schedulerLock: SchedulerLock,
    private val mongoTemplate: MongoTemplate
) {

    @BeforeEach
    fun setUp() {
        mongoTemplate.dropCollection(SchedulerLockDocument::class.java)
    }

    @Test
    fun `lock 만료 전에는 다른 owner가 획득할 수 없고 만료 후에는 획득할 수 있다`() {
        val now = Instant.parse("2026-04-08T02:00:00Z")

        val acquired = schedulerLock.tryAcquire("inventory-rolling", "app-1", now.plusSeconds(60), now)
        val blocked = schedulerLock.tryAcquire("inventory-rolling", "app-2", now.plusSeconds(90), now.plusSeconds(30))
        val reclaimed = schedulerLock.tryAcquire("inventory-rolling", "app-2", now.plusSeconds(130), now.plusSeconds(61))

        assertThat(acquired).isTrue()
        assertThat(blocked).isFalse()
        assertThat(reclaimed).isTrue()
    }

    @Test
    fun `owner가 release하면 다른 owner가 즉시 획득할 수 있다`() {
        val now = Instant.parse("2026-04-08T02:00:00Z")

        schedulerLock.tryAcquire("inventory-rolling", "app-1", now.plusSeconds(60), now)
        schedulerLock.release("inventory-rolling", "app-1")

        val acquired = schedulerLock.tryAcquire("inventory-rolling", "app-2", now.plusSeconds(90), now.plusSeconds(10))

        assertThat(acquired).isTrue()
    }
}
