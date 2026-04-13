package com.stayops.payment.domain.model

import com.stayops.shared.domain.Money
import java.time.Duration
import java.time.Instant

@ConsistentCopyVisibility
data class PaymentOutboxMessage private constructor(
    val id: String,
    val paymentId: String,
    val reservationId: String,
    val memberId: String,
    val type: PaymentOutboxType,
    val paymentKey: String,
    val orderId: String,
    val amount: Money,
    val cancelReason: String?,
    val idempotencyKey: String,
    val status: PaymentOutboxStatus,
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

    fun startProcessing(workerId: String, lockedUntil: Instant, now: Instant = Instant.now()): PaymentOutboxMessage {
        require(workerId.isNotBlank()) { "workerId는 필수입니다" }
        require(lockedUntil.isAfter(now)) { "lockedUntil은 현재 시각보다 이후여야 합니다" }

        val canStartPending = status == PaymentOutboxStatus.PENDING &&
            (nextRetryAt == null || !nextRetryAt.isAfter(now))
        val canReclaimExpiredLease = status == PaymentOutboxStatus.IN_PROGRESS &&
            this.lockedUntil != null &&
            !this.lockedUntil.isAfter(now)

        check(canStartPending || canReclaimExpiredLease) {
            "처리 가능한 Outbox 상태가 아닙니다: status=$status, nextRetryAt=$nextRetryAt, lockedUntil=${this.lockedUntil}"
        }

        return copy(
            status = PaymentOutboxStatus.IN_PROGRESS,
            lockedBy = workerId,
            lockedUntil = lockedUntil,
            updatedAt = now
        )
    }

    fun complete(now: Instant = Instant.now()): PaymentOutboxMessage {
        check(status == PaymentOutboxStatus.IN_PROGRESS) {
            "IN_PROGRESS 상태에서만 완료할 수 있습니다: $status"
        }
        return copy(
            status = PaymentOutboxStatus.COMPLETED,
            nextRetryAt = null,
            lockedBy = null,
            lockedUntil = null,
            lastError = null,
            updatedAt = now
        )
    }

    fun skip(reason: String, now: Instant = Instant.now()): PaymentOutboxMessage {
        check(status == PaymentOutboxStatus.IN_PROGRESS) {
            "IN_PROGRESS 상태에서만 건너뛸 수 있습니다: $status"
        }
        return copy(
            status = PaymentOutboxStatus.SKIPPED,
            nextRetryAt = null,
            lockedBy = null,
            lockedUntil = null,
            lastError = reason,
            updatedAt = now
        )
    }

    fun fail(errorMessage: String, now: Instant = Instant.now()): PaymentOutboxMessage {
        check(status == PaymentOutboxStatus.IN_PROGRESS) {
            "IN_PROGRESS 상태에서만 실패 처리할 수 있습니다: $status"
        }

        val newRetryCount = retryCount + 1
        val reachedMax = newRetryCount >= maxRetries

        return copy(
            status = if (reachedMax) PaymentOutboxStatus.FAILED else PaymentOutboxStatus.PENDING,
            retryCount = newRetryCount,
            nextRetryAt = if (reachedMax) null else now + backoffDelay(newRetryCount),
            lockedBy = null,
            lockedUntil = null,
            lastError = errorMessage,
            updatedAt = now
        )
    }

    companion object {
        private const val BASE_DELAY_SECONDS = 30L
        private const val DEFAULT_MAX_RETRIES = 3

        private fun backoffDelay(retryCount: Int): Duration =
            Duration.ofSeconds(BASE_DELAY_SECONDS * (1L shl (retryCount - 1)))

        fun createConfirm(
            id: String,
            paymentId: String,
            reservationId: String,
            memberId: String,
            paymentKey: String,
            orderId: String,
            amount: Money,
            now: Instant = Instant.now()
        ): PaymentOutboxMessage {
            require(id.isNotBlank()) { "id는 필수입니다" }
            require(paymentId.isNotBlank()) { "paymentId는 필수입니다" }
            require(reservationId.isNotBlank()) { "reservationId는 필수입니다" }
            require(memberId.isNotBlank()) { "memberId는 필수입니다" }
            require(paymentKey.isNotBlank()) { "paymentKey는 필수입니다" }
            require(orderId.isNotBlank()) { "orderId는 필수입니다" }

            return PaymentOutboxMessage(
                id = id,
                paymentId = paymentId,
                reservationId = reservationId,
                memberId = memberId,
                type = PaymentOutboxType.CONFIRM_PAYMENT,
                paymentKey = paymentKey,
                orderId = orderId,
                amount = amount,
                cancelReason = null,
                idempotencyKey = "payment-confirm:$paymentId:$orderId",
                status = PaymentOutboxStatus.PENDING,
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

        fun createCancel(
            id: String,
            paymentId: String,
            reservationId: String,
            memberId: String,
            paymentKey: String,
            orderId: String,
            amount: Money,
            cancelReason: String,
            now: Instant = Instant.now()
        ): PaymentOutboxMessage {
            require(id.isNotBlank()) { "id는 필수입니다" }
            require(paymentId.isNotBlank()) { "paymentId는 필수입니다" }
            require(reservationId.isNotBlank()) { "reservationId는 필수입니다" }
            require(memberId.isNotBlank()) { "memberId는 필수입니다" }
            require(paymentKey.isNotBlank()) { "paymentKey는 필수입니다" }
            require(orderId.isNotBlank()) { "orderId는 필수입니다" }
            require(cancelReason.isNotBlank()) { "cancelReason은 필수입니다" }

            return PaymentOutboxMessage(
                id = id,
                paymentId = paymentId,
                reservationId = reservationId,
                memberId = memberId,
                type = PaymentOutboxType.CANCEL_PAYMENT,
                paymentKey = paymentKey,
                orderId = orderId,
                amount = amount,
                cancelReason = cancelReason,
                idempotencyKey = "payment-cancel:$paymentId:$orderId",
                status = PaymentOutboxStatus.PENDING,
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
            paymentId: String,
            reservationId: String,
            memberId: String,
            type: PaymentOutboxType,
            paymentKey: String,
            orderId: String,
            amount: Money,
            cancelReason: String?,
            idempotencyKey: String,
            status: PaymentOutboxStatus,
            retryCount: Int,
            maxRetries: Int,
            nextRetryAt: Instant?,
            lockedBy: String?,
            lockedUntil: Instant?,
            lastError: String?,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): PaymentOutboxMessage = PaymentOutboxMessage(
            id = id,
            paymentId = paymentId,
            reservationId = reservationId,
            memberId = memberId,
            type = type,
            paymentKey = paymentKey,
            orderId = orderId,
            amount = amount,
            cancelReason = cancelReason,
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
