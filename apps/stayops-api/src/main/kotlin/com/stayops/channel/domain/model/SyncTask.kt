package com.stayops.channel.domain.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

@ConsistentCopyVisibility
data class SyncTask private constructor(
    val id: String,
    val propertyId: String,
    val channelCode: String,
    val type: SyncTaskType,
    val payload: Map<String, Any>,
    val idempotencyKey: String,
    val status: SyncTaskStatus,
    val retryCount: Int,
    val maxRetries: Int,
    val nextRetryAt: Instant?,
    val lockedBy: String?,
    val lockedUntil: Instant?,
    val lastError: String?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    fun startProcessing(now: Instant = Instant.now()): SyncTask =
        startProcessing(DEFAULT_WORKER_ID, now.plus(DEFAULT_LEASE_DURATION), now)

    fun startProcessing(workerId: String, lockedUntil: Instant, now: Instant = Instant.now()): SyncTask {
        require(workerId.isNotBlank()) { "workerId는 필수입니다" }
        require(lockedUntil.isAfter(now)) { "lockedUntil은 현재 시각보다 이후여야 합니다" }

        val canStartPending = status == SyncTaskStatus.PENDING &&
            (nextRetryAt == null || !nextRetryAt.isAfter(now))
        val canReclaimExpiredLease = status == SyncTaskStatus.IN_PROGRESS &&
            this.lockedUntil != null &&
            !this.lockedUntil.isAfter(now)

        check(canStartPending || canReclaimExpiredLease) {
            "처리 가능한 SyncTask 상태가 아닙니다: status=$status, nextRetryAt=$nextRetryAt, lockedUntil=${this.lockedUntil}"
        }
        return copy(
            status = SyncTaskStatus.IN_PROGRESS,
            lockedBy = workerId,
            lockedUntil = lockedUntil,
            updatedAt = now
        )
    }

    fun complete(now: Instant = Instant.now()): SyncTask {
        check(status == SyncTaskStatus.IN_PROGRESS) {
            "IN_PROGRESS 상태에서만 완료할 수 있습니다: $status"
        }
        return copy(
            status = SyncTaskStatus.COMPLETED,
            nextRetryAt = null,
            lockedBy = null,
            lockedUntil = null,
            updatedAt = now
        )
    }

    fun skip(reason: String, now: Instant = Instant.now()): SyncTask {
        check(status == SyncTaskStatus.IN_PROGRESS) {
            "IN_PROGRESS 상태에서만 건너뛸 수 있습니다: $status"
        }
        return copy(
            status = SyncTaskStatus.SKIPPED,
            lastError = reason,
            nextRetryAt = null,
            lockedBy = null,
            lockedUntil = null,
            updatedAt = now
        )
    }

    fun fail(errorMessage: String, now: Instant = Instant.now()): SyncTask {
        check(status == SyncTaskStatus.IN_PROGRESS) {
            "IN_PROGRESS 상태에서만 실패 처리할 수 있습니다: $status"
        }
        val newRetryCount = retryCount + 1
        val reachedMax = newRetryCount >= maxRetries

        return copy(
            status = if (reachedMax) SyncTaskStatus.FAILED else SyncTaskStatus.PENDING,
            retryCount = newRetryCount,
            nextRetryAt = if (reachedMax) null else now + backoffDelay(newRetryCount),
            lockedBy = null,
            lockedUntil = null,
            lastError = errorMessage,
            updatedAt = now
        )
    }

    fun retry(now: Instant = Instant.now()): SyncTask {
        check(status == SyncTaskStatus.FAILED) {
            "FAILED 상태에서만 수동 재시도할 수 있습니다: $status"
        }
        return copy(
            status = SyncTaskStatus.PENDING,
            retryCount = 0,
            nextRetryAt = null,
            lockedBy = null,
            lockedUntil = null,
            lastError = null,
            updatedAt = now
        )
    }

    companion object {
        private const val BASE_DELAY_SECONDS = 30L
        private const val DEFAULT_MAX_RETRIES = 3
        private const val DEFAULT_WORKER_ID = "sync-task-worker"
        private val DEFAULT_LEASE_DURATION: Duration = Duration.ofSeconds(60)

        private fun backoffDelay(retryCount: Int): Duration =
            Duration.ofSeconds(BASE_DELAY_SECONDS * (1L shl (retryCount - 1)))

        fun create(
            id: String,
            propertyId: String,
            channelCode: String,
            type: SyncTaskType,
            payload: Map<String, Any>,
            now: Instant = Instant.now()
        ): SyncTask {
            return SyncTask(
                id = id,
                propertyId = propertyId,
                channelCode = channelCode,
                type = type,
                payload = payload,
                idempotencyKey = UUID.randomUUID().toString(),
                status = SyncTaskStatus.PENDING,
                retryCount = 0,
                maxRetries = DEFAULT_MAX_RETRIES,
                nextRetryAt = null,
                lockedBy = null,
                lockedUntil = null,
                lastError = null,
                version = 0L,
                createdAt = now,
                updatedAt = now
            )
        }

        fun reconstitute(
            id: String,
            propertyId: String,
            channelCode: String,
            type: SyncTaskType,
            payload: Map<String, Any>,
            idempotencyKey: String,
            status: SyncTaskStatus,
            retryCount: Int,
            maxRetries: Int,
            nextRetryAt: Instant?,
            lockedBy: String?,
            lockedUntil: Instant?,
            lastError: String?,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): SyncTask = SyncTask(
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
            lockedBy = lockedBy,
            lockedUntil = lockedUntil,
            lastError = lastError,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
