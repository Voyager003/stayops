package com.stayops.channel.infrastructure.persistence

import com.stayops.shared.config.MongoPersistence

import com.stayops.channel.domain.model.SyncTask
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.repository.SyncTaskRepository
import com.stayops.channel.infrastructure.persistence.dao.SyncTaskMongoDao
import com.stayops.shared.exception.ConflictException
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import java.time.Instant

@MongoPersistence
@Repository
class MongoSyncTaskRepository(
    private val mongo: SyncTaskMongoDao,
    private val mongoTemplate: MongoTemplate
) : SyncTaskRepository {

    @PostConstruct
    fun createIndexes() {
        val indexOps = mongoTemplate.indexOps(SyncTaskDocument::class.java)

        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("status" to 1, "nextRetryAt" to 1)))
        )
        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("status" to 1, "lockedUntil" to 1)))
        )
        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("propertyId" to 1, "channelCode" to 1, "status" to 1)))
        )
    }

    override fun save(task: SyncTask): SyncTask =
        try {
            mongo.save(SyncTaskDocument.from(task)).toDomain()
        } catch (e: OptimisticLockingFailureException) {
            throw ConflictException(
                code = "SYNC_TASK_CONFLICT",
                message = "SyncTask 버전 충돌이 발생했습니다: ${task.id}"
            )
        }

    override fun findById(id: String): SyncTask? =
        mongo.findByIdOrNull(id)?.toDomain()

    override fun claimReadyForProcessing(workerId: String, now: Instant, lockedUntil: Instant): SyncTask? {
        val query = Query(readyCriteria(now))
            .with(Sort.by(Sort.Direction.ASC, "createdAt"))

        val update = Update()
            .set("status", SyncTaskStatus.IN_PROGRESS)
            .set("lockedBy", workerId)
            .set("lockedUntil", lockedUntil)
            .set("updatedAt", now)
            .inc("version", 1)

        return mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            SyncTaskDocument::class.java
        )?.toDomain()
    }

    override fun findPendingTasksReadyForProcessing(now: Instant): List<SyncTask> {
        val query = Query(readyCriteria(now))
        return mongoTemplate.find(query, SyncTaskDocument::class.java).map { it.toDomain() }
    }

    private fun readyCriteria(now: Instant): Criteria {
        val pending = Criteria.where("status").`is`(SyncTaskStatus.PENDING)
            .andOperator(
                Criteria().orOperator(
                    Criteria.where("nextRetryAt").`is`(null),
                    Criteria.where("nextRetryAt").lte(now)
                )
            )
        val expiredLease = Criteria.where("status").`is`(SyncTaskStatus.IN_PROGRESS)
            .and("lockedUntil").lte(now)

        return Criteria().orOperator(pending, expiredLease)
    }

    override fun findByPropertyIdAndStatus(propertyId: String, status: SyncTaskStatus): List<SyncTask> =
        mongo.findByPropertyIdAndStatus(propertyId, status).map { it.toDomain() }

    override fun findByPropertyIdAndChannelCodeAndStatus(
        propertyId: String,
        channelCode: String,
        status: SyncTaskStatus
    ): List<SyncTask> =
        mongo.findByPropertyIdAndChannelCodeAndStatus(propertyId, channelCode, status).map { it.toDomain() }

    override fun countByPropertyIdAndChannelCodeGroupByStatus(
        propertyId: String,
        channelCode: String
    ): Map<SyncTaskStatus, Long> {
        val aggregation = Aggregation.newAggregation(
            Aggregation.match(
                Criteria.where("propertyId").`is`(propertyId)
                    .and("channelCode").`is`(channelCode)
            ),
            Aggregation.group("status").count().`as`("count"),
            Aggregation.project("count").and("_id").`as`("status")
        )
        data class StatusCount(val status: SyncTaskStatus, val count: Long)
        val results = mongoTemplate.aggregate(aggregation, "sync_tasks", StatusCount::class.java)
        return results.mappedResults.associate { it.status to it.count }
    }
}
