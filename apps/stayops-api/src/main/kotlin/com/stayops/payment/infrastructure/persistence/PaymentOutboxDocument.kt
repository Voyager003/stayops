package com.stayops.payment.infrastructure.persistence

import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.shared.domain.Money
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

@Document("payment_outbox_messages")
data class PaymentOutboxDocument(
    @Id val id: String,
    val paymentId: String,
    val reservationId: String?,
    val reservationIntentId: String? = null,
    val memberId: String,
    val type: PaymentOutboxType,
    val paymentKey: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val cancelReason: String?,
    val idempotencyKey: String,
    val status: PaymentOutboxStatus,
    val retryCount: Int,
    val maxRetries: Int,
    val nextRetryAt: Instant?,
    val lockedBy: String?,
    val lockedUntil: Instant?,
    val lastError: String?,
    @Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    fun toDomain(): PaymentOutboxMessage = PaymentOutboxMessage.reconstitute(
        id = id,
        paymentId = paymentId,
        reservationId = reservationId,
        reservationIntentId = reservationIntentId,
        memberId = memberId,
        type = type,
        paymentKey = paymentKey,
        orderId = orderId,
        amount = Money.of(amount, currency),
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

    companion object {
        fun from(message: PaymentOutboxMessage): PaymentOutboxDocument = PaymentOutboxDocument(
            id = message.id,
            paymentId = message.paymentId,
            reservationId = message.reservationId,
            reservationIntentId = message.reservationIntentId,
            memberId = message.memberId,
            type = message.type,
            paymentKey = message.paymentKey,
            orderId = message.orderId,
            amount = message.amount.amount,
            currency = message.amount.currency,
            cancelReason = message.cancelReason,
            idempotencyKey = message.idempotencyKey,
            status = message.status,
            retryCount = message.retryCount,
            maxRetries = message.maxRetries,
            nextRetryAt = message.nextRetryAt,
            lockedBy = message.lockedBy,
            lockedUntil = message.lockedUntil,
            lastError = message.lastError,
            version = message.version,
            createdAt = message.createdAt,
            updatedAt = message.updatedAt
        )
    }
}
