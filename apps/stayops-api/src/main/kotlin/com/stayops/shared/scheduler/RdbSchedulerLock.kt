package com.stayops.shared.scheduler

import com.stayops.jooq.generated.Tables.SCHEDULER_LOCKS
import com.stayops.shared.config.RdbPersistence
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Component
class RdbSchedulerLock(
    private val dsl: DSLContext
) : SchedulerLock {

    override fun tryAcquire(name: String, owner: String, lockedUntil: Instant, now: Instant): Boolean {
        require(name.isNotBlank()) { "lock name은 필수입니다" }
        require(owner.isNotBlank()) { "lock owner는 필수입니다" }
        require(lockedUntil.isAfter(now)) { "lockedUntil은 현재 시각보다 이후여야 합니다" }

        val affectedRows = dsl.insertInto(SCHEDULER_LOCKS)
            .set(SCHEDULER_LOCKS.NAME, name)
            .set(SCHEDULER_LOCKS.LOCKED_BY, owner)
            .set(SCHEDULER_LOCKS.LOCKED_UNTIL, lockedUntil.toOffsetDateTime())
            .set(SCHEDULER_LOCKS.UPDATED_AT, now.toOffsetDateTime())
            .onConflict(SCHEDULER_LOCKS.NAME)
            .doUpdate()
            .set(SCHEDULER_LOCKS.LOCKED_BY, owner)
            .set(SCHEDULER_LOCKS.LOCKED_UNTIL, lockedUntil.toOffsetDateTime())
            .set(SCHEDULER_LOCKS.UPDATED_AT, now.toOffsetDateTime())
            .where(SCHEDULER_LOCKS.LOCKED_UNTIL.le(now.toOffsetDateTime()))
            .execute()

        return affectedRows == 1
    }

    override fun release(name: String, owner: String) {
        dsl.deleteFrom(SCHEDULER_LOCKS)
            .where(SCHEDULER_LOCKS.NAME.eq(name))
            .and(SCHEDULER_LOCKS.LOCKED_BY.eq(owner))
            .execute()
    }

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
