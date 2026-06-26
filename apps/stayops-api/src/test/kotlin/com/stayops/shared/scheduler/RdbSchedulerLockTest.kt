package com.stayops.shared.scheduler

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.jooq.generated.Tables.SCHEDULER_LOCKS
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.time.Instant

@SpringJUnitConfig(classes = [RdbSchedulerLockTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbSchedulerLockTest @Autowired constructor(
    private val schedulerLock: SchedulerLock,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, RdbSchedulerLock::class)
    class TestApplication

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(SCHEDULER_LOCKS).execute()
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
