package com.stayops.channel.infrastructure.persistence.rdb

import com.stayops.channel.domain.model.SyncTask
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.model.SyncTaskType
import com.stayops.channel.domain.repository.SyncTaskRepository
import com.stayops.jooq.generated.Tables.SYNC_TASKS
import com.stayops.jooq.generated.tables.records.SyncTasksRecord
import com.stayops.shared.config.RdbPersistence
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.JSON
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbSyncTaskRepository(
    private val dsl: DSLContext,
    private val objectMapper: ObjectMapper
) : SyncTaskRepository {

    override fun save(task: SyncTask): SyncTask {
        dsl.insertInto(SYNC_TASKS)
            .set(SYNC_TASKS.ID, task.id)
            .set(SYNC_TASKS.PROPERTY_ID, task.propertyId)
            .set(SYNC_TASKS.CHANNEL_CODE, task.channelCode)
            .set(SYNC_TASKS.TYPE, task.type.name)
            .set(SYNC_TASKS.PAYLOAD, JSON.valueOf(objectMapper.writeValueAsString(task.payload)))
            .set(SYNC_TASKS.IDEMPOTENCY_KEY, task.idempotencyKey)
            .set(SYNC_TASKS.STATUS, task.status.name)
            .set(SYNC_TASKS.RETRY_COUNT, task.retryCount)
            .set(SYNC_TASKS.MAX_RETRIES, task.maxRetries)
            .set(SYNC_TASKS.NEXT_RETRY_AT, task.nextRetryAt?.toOffsetDateTime())
            .set(SYNC_TASKS.LOCKED_BY, task.lockedBy)
            .set(SYNC_TASKS.LOCKED_UNTIL, task.lockedUntil?.toOffsetDateTime())
            .set(SYNC_TASKS.LAST_ERROR, task.lastError)
            .set(SYNC_TASKS.VERSION, task.version)
            .set(SYNC_TASKS.CREATED_AT, task.createdAt.toOffsetDateTime())
            .set(SYNC_TASKS.UPDATED_AT, task.updatedAt.toOffsetDateTime())
            .onConflict(SYNC_TASKS.ID)
            .doUpdate()
            .set(SYNC_TASKS.PROPERTY_ID, task.propertyId)
            .set(SYNC_TASKS.CHANNEL_CODE, task.channelCode)
            .set(SYNC_TASKS.TYPE, task.type.name)
            .set(SYNC_TASKS.PAYLOAD, JSON.valueOf(objectMapper.writeValueAsString(task.payload)))
            .set(SYNC_TASKS.IDEMPOTENCY_KEY, task.idempotencyKey)
            .set(SYNC_TASKS.STATUS, task.status.name)
            .set(SYNC_TASKS.RETRY_COUNT, task.retryCount)
            .set(SYNC_TASKS.MAX_RETRIES, task.maxRetries)
            .set(SYNC_TASKS.NEXT_RETRY_AT, task.nextRetryAt?.toOffsetDateTime())
            .set(SYNC_TASKS.LOCKED_BY, task.lockedBy)
            .set(SYNC_TASKS.LOCKED_UNTIL, task.lockedUntil?.toOffsetDateTime())
            .set(SYNC_TASKS.LAST_ERROR, task.lastError)
            .set(SYNC_TASKS.VERSION, task.version)
            .set(SYNC_TASKS.CREATED_AT, task.createdAt.toOffsetDateTime())
            .set(SYNC_TASKS.UPDATED_AT, task.updatedAt.toOffsetDateTime())
            .execute()

        return findById(task.id) ?: task
    }

    override fun findById(id: String): SyncTask? =
        dsl.selectFrom(SYNC_TASKS)
            .where(SYNC_TASKS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    override fun claimReadyForProcessing(workerId: String, now: Instant, lockedUntil: Instant): SyncTask? =
        dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)
            val taskId = tx.select(SYNC_TASKS.ID)
                .from(SYNC_TASKS)
                .where(readyCondition(now))
                .orderBy(SYNC_TASKS.CREATED_AT.asc(), SYNC_TASKS.ID.asc())
                .limit(1)
                .forUpdate()
                .skipLocked()
                .fetchOne(SYNC_TASKS.ID)
                ?: return@transactionResult null

            tx.update(SYNC_TASKS)
                .set(SYNC_TASKS.STATUS, SyncTaskStatus.IN_PROGRESS.name)
                .set(SYNC_TASKS.LOCKED_BY, workerId)
                .set(SYNC_TASKS.LOCKED_UNTIL, lockedUntil.toOffsetDateTime())
                .set(SYNC_TASKS.UPDATED_AT, now.toOffsetDateTime())
                .set(SYNC_TASKS.VERSION, SYNC_TASKS.VERSION.plus(1))
                .where(SYNC_TASKS.ID.eq(taskId))
                .execute()

            tx.selectFrom(SYNC_TASKS)
                .where(SYNC_TASKS.ID.eq(taskId))
                .fetchOne()
                ?.toDomain()
        }

    override fun findPendingTasksReadyForProcessing(now: Instant): List<SyncTask> =
        findAll(readyCondition(now))

    override fun findByPropertyIdAndStatus(propertyId: String, status: SyncTaskStatus): List<SyncTask> =
        findAll(
            SYNC_TASKS.PROPERTY_ID.eq(propertyId)
                .and(SYNC_TASKS.STATUS.eq(status.name))
        )

    override fun findByPropertyIdAndChannelCodeAndStatus(
        propertyId: String,
        channelCode: String,
        status: SyncTaskStatus
    ): List<SyncTask> =
        findAll(
            SYNC_TASKS.PROPERTY_ID.eq(propertyId)
                .and(SYNC_TASKS.CHANNEL_CODE.eq(channelCode))
                .and(SYNC_TASKS.STATUS.eq(status.name))
        )

    override fun countByPropertyIdAndChannelCodeGroupByStatus(
        propertyId: String,
        channelCode: String
    ): Map<SyncTaskStatus, Long> =
        dsl.select(SYNC_TASKS.STATUS, DSL.count())
            .from(SYNC_TASKS)
            .where(SYNC_TASKS.PROPERTY_ID.eq(propertyId))
            .and(SYNC_TASKS.CHANNEL_CODE.eq(channelCode))
            .groupBy(SYNC_TASKS.STATUS)
            .fetchMap(
                { record -> SyncTaskStatus.valueOf(record.get(SYNC_TASKS.STATUS)) },
                { record -> record.get(DSL.count()).toLong() }
            )

    private fun readyCondition(now: Instant): Condition =
        SYNC_TASKS.STATUS.eq(SyncTaskStatus.PENDING.name)
            .and(SYNC_TASKS.NEXT_RETRY_AT.isNull.or(SYNC_TASKS.NEXT_RETRY_AT.le(now.toOffsetDateTime())))
            .or(
                SYNC_TASKS.STATUS.eq(SyncTaskStatus.IN_PROGRESS.name)
                    .and(SYNC_TASKS.LOCKED_UNTIL.le(now.toOffsetDateTime()))
            )

    private fun findAll(condition: Condition): List<SyncTask> =
        dsl.selectFrom(SYNC_TASKS)
            .where(condition)
            .orderBy(SYNC_TASKS.CREATED_AT.asc(), SYNC_TASKS.ID.asc())
            .fetch { record -> record.toDomain() }

    @Suppress("UNCHECKED_CAST")
    private fun SyncTasksRecord.toDomain(): SyncTask =
        SyncTask.reconstitute(
            id = get(SYNC_TASKS.ID),
            propertyId = get(SYNC_TASKS.PROPERTY_ID),
            channelCode = get(SYNC_TASKS.CHANNEL_CODE),
            type = SyncTaskType.valueOf(get(SYNC_TASKS.TYPE)),
            payload = objectMapper.readValue(get(SYNC_TASKS.PAYLOAD).data(), Map::class.java) as Map<String, Any>,
            idempotencyKey = get(SYNC_TASKS.IDEMPOTENCY_KEY),
            status = SyncTaskStatus.valueOf(get(SYNC_TASKS.STATUS)),
            retryCount = get(SYNC_TASKS.RETRY_COUNT),
            maxRetries = get(SYNC_TASKS.MAX_RETRIES),
            nextRetryAt = get(SYNC_TASKS.NEXT_RETRY_AT)?.toInstant(),
            lockedBy = get(SYNC_TASKS.LOCKED_BY),
            lockedUntil = get(SYNC_TASKS.LOCKED_UNTIL)?.toInstant(),
            lastError = get(SYNC_TASKS.LAST_ERROR),
            version = get(SYNC_TASKS.VERSION),
            createdAt = get(SYNC_TASKS.CREATED_AT).toInstant(),
            updatedAt = get(SYNC_TASKS.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
