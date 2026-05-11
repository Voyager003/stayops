package com.stayops.shared.scheduler

import org.springframework.dao.DuplicateKeyException
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class MongoSchedulerLock(
    private val mongoTemplate: MongoTemplate
) : SchedulerLock {

    override fun tryAcquire(name: String, owner: String, lockedUntil: Instant, now: Instant): Boolean {
        require(name.isNotBlank()) { "lock name은 필수입니다" }
        require(owner.isNotBlank()) { "lock owner는 필수입니다" }
        require(lockedUntil.isAfter(now)) { "lockedUntil은 현재 시각보다 이후여야 합니다" }

        val query = Query(
            Criteria.where("_id").`is`(name)
                .orOperator(
                    Criteria.where("lockedUntil").lte(now),
                    Criteria.where("lockedUntil").exists(false)
                )
        )
        val update = Update()
            .setOnInsert("_id", name)
            .set("lockedBy", owner)
            .set("lockedUntil", lockedUntil)
            .set("updatedAt", now)
        val options = FindAndModifyOptions.options().upsert(true).returnNew(true)

        val lock = try {
            mongoTemplate.findAndModify(query, update, options, SchedulerLockDocument::class.java)
        } catch (_: DuplicateKeyException) {
            return false
        }
        return lock?.lockedBy == owner && lock.lockedUntil == lockedUntil
    }

    override fun release(name: String, owner: String) {
        val query = Query(
            Criteria.where("_id").`is`(name)
                .and("lockedBy").`is`(owner)
        )
        mongoTemplate.remove(query, SchedulerLockDocument::class.java)
    }
}

@Document("scheduler_locks")
data class SchedulerLockDocument(
    @Id val id: String,
    val lockedBy: String,
    val lockedUntil: Instant,
    val updatedAt: Instant
)
