package com.stayops.channel.api.dto

import com.stayops.channel.domain.model.SyncTask
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.model.SyncTaskType
import java.time.Instant

data class SyncTaskResponse(
    val id: String,
    val channelCode: String,
    val type: SyncTaskType,
    val status: SyncTaskStatus,
    val retryCount: Int,
    val maxRetries: Int,
    val nextRetryAt: Instant?,
    val lastError: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(task: SyncTask) = SyncTaskResponse(
            id = task.id,
            channelCode = task.channelCode,
            type = task.type,
            status = task.status,
            retryCount = task.retryCount,
            maxRetries = task.maxRetries,
            nextRetryAt = task.nextRetryAt,
            lastError = task.lastError,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt
        )
    }
}
