package com.stayops.channel.infrastructure.persistence

import com.stayops.channel.domain.model.SyncTask
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.model.SyncTaskType
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("sync_tasks")
data class SyncTaskDocument(
    @Id val id: String,
    val propertyId: String,
    val channelCode: String,
    val type: SyncTaskType,
    val payload: Map<String, Any>,
    val idempotencyKey: String,
    val status: SyncTaskStatus,
    val retryCount: Int,
    val maxRetries: Int,
    val nextRetryAt: Instant?,
    val lastError: String?,
    @Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    fun toDomain(): SyncTask = SyncTask.reconstitute(
        id = id,
        propertyId = propertyId,
        channelCode = channelCode,
        type = type,
        payload = payload,
        idempotencyKey = idempotencyKey,
        status = status,
        retryCount = retryCount,
        maxRetries = maxRetries,
        nextRetryAt = nextRetryAt,
        lastError = lastError,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(task: SyncTask): SyncTaskDocument = SyncTaskDocument(
            id = task.id,
            propertyId = task.propertyId,
            channelCode = task.channelCode,
            type = task.type,
            payload = task.payload,
            idempotencyKey = task.idempotencyKey,
            status = task.status,
            retryCount = task.retryCount,
            maxRetries = task.maxRetries,
            nextRetryAt = task.nextRetryAt,
            lastError = task.lastError,
            version = task.version,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt
        )
    }
}
