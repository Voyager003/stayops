package com.stayops.channel.infrastructure.persistence

import com.stayops.channel.domain.model.SyncTask
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.repository.SyncTaskRepository
import com.stayops.shared.exception.ConflictException
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class MongoSyncTaskRepository(
    private val mongo: SyncTaskMongoDataRepository,
    private val mongoTemplate: MongoTemplate
) : SyncTaskRepository {

    @PostConstruct
    fun createIndexes() {
        val indexOps = mongoTemplate.indexOps(SyncTaskDocument::class.java)

        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("status" to 1, "nextRetryAt" to 1)))
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

    override fun findPendingTasksReadyForProcessing(now: Instant): List<SyncTask> {
        val query = Query(
            Criteria.where("status").`is`(SyncTaskStatus.PENDING)
                .andOperator(
                    Criteria().orOperator(
                        Criteria.where("nextRetryAt").`is`(null),
                        Criteria.where("nextRetryAt").lte(now)
                    )
                )
        )
        return mongoTemplate.find(query, SyncTaskDocument::class.java).map { it.toDomain() }
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

interface SyncTaskMongoDataRepository : MongoRepository<SyncTaskDocument, String> {
    fun findByPropertyIdAndStatus(propertyId: String, status: SyncTaskStatus): List<SyncTaskDocument>
    fun findByPropertyIdAndChannelCodeAndStatus(
        propertyId: String,
        channelCode: String,
        status: SyncTaskStatus
    ): List<SyncTaskDocument>
}
