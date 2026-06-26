package com.stayops.payment.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.PAYMENT_OUTBOX_MESSAGES
import com.stayops.jooq.generated.tables.records.PaymentOutboxMessagesRecord
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.shared.config.RdbPersistence
import com.stayops.shared.domain.Money
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbPaymentOutboxRepository(
    private val dsl: DSLContext
) : PaymentOutboxRepository {

    override fun save(message: PaymentOutboxMessage): PaymentOutboxMessage {
        dsl.insertInto(PAYMENT_OUTBOX_MESSAGES)
            .set(PAYMENT_OUTBOX_MESSAGES.ID, message.id)
            .set(PAYMENT_OUTBOX_MESSAGES.PAYMENT_ID, message.paymentId)
            .set(PAYMENT_OUTBOX_MESSAGES.RESERVATION_ID, message.reservationId)
            .set(PAYMENT_OUTBOX_MESSAGES.MEMBER_ID, message.memberId)
            .set(PAYMENT_OUTBOX_MESSAGES.TYPE, message.type.name)
            .set(PAYMENT_OUTBOX_MESSAGES.PAYMENT_KEY, message.paymentKey)
            .set(PAYMENT_OUTBOX_MESSAGES.ORDER_ID, message.orderId)
            .set(PAYMENT_OUTBOX_MESSAGES.AMOUNT, message.amount.amount)
            .set(PAYMENT_OUTBOX_MESSAGES.CURRENCY, message.amount.currency)
            .set(PAYMENT_OUTBOX_MESSAGES.CANCEL_REASON, message.cancelReason)
            .set(PAYMENT_OUTBOX_MESSAGES.IDEMPOTENCY_KEY, message.idempotencyKey)
            .set(PAYMENT_OUTBOX_MESSAGES.STATUS, message.status.name)
            .set(PAYMENT_OUTBOX_MESSAGES.RETRY_COUNT, message.retryCount)
            .set(PAYMENT_OUTBOX_MESSAGES.MAX_RETRIES, message.maxRetries)
            .set(PAYMENT_OUTBOX_MESSAGES.NEXT_RETRY_AT, message.nextRetryAt?.toOffsetDateTime())
            .set(PAYMENT_OUTBOX_MESSAGES.LOCKED_BY, message.lockedBy)
            .set(PAYMENT_OUTBOX_MESSAGES.LOCKED_UNTIL, message.lockedUntil?.toOffsetDateTime())
            .set(PAYMENT_OUTBOX_MESSAGES.LAST_ERROR, message.lastError)
            .set(PAYMENT_OUTBOX_MESSAGES.VERSION, message.version)
            .set(PAYMENT_OUTBOX_MESSAGES.CREATED_AT, message.createdAt.toOffsetDateTime())
            .set(PAYMENT_OUTBOX_MESSAGES.UPDATED_AT, message.updatedAt.toOffsetDateTime())
            .onConflict(PAYMENT_OUTBOX_MESSAGES.ID)
            .doUpdate()
            .set(PAYMENT_OUTBOX_MESSAGES.PAYMENT_ID, message.paymentId)
            .set(PAYMENT_OUTBOX_MESSAGES.RESERVATION_ID, message.reservationId)
            .set(PAYMENT_OUTBOX_MESSAGES.MEMBER_ID, message.memberId)
            .set(PAYMENT_OUTBOX_MESSAGES.TYPE, message.type.name)
            .set(PAYMENT_OUTBOX_MESSAGES.PAYMENT_KEY, message.paymentKey)
            .set(PAYMENT_OUTBOX_MESSAGES.ORDER_ID, message.orderId)
            .set(PAYMENT_OUTBOX_MESSAGES.AMOUNT, message.amount.amount)
            .set(PAYMENT_OUTBOX_MESSAGES.CURRENCY, message.amount.currency)
            .set(PAYMENT_OUTBOX_MESSAGES.CANCEL_REASON, message.cancelReason)
            .set(PAYMENT_OUTBOX_MESSAGES.IDEMPOTENCY_KEY, message.idempotencyKey)
            .set(PAYMENT_OUTBOX_MESSAGES.STATUS, message.status.name)
            .set(PAYMENT_OUTBOX_MESSAGES.RETRY_COUNT, message.retryCount)
            .set(PAYMENT_OUTBOX_MESSAGES.MAX_RETRIES, message.maxRetries)
            .set(PAYMENT_OUTBOX_MESSAGES.NEXT_RETRY_AT, message.nextRetryAt?.toOffsetDateTime())
            .set(PAYMENT_OUTBOX_MESSAGES.LOCKED_BY, message.lockedBy)
            .set(PAYMENT_OUTBOX_MESSAGES.LOCKED_UNTIL, message.lockedUntil?.toOffsetDateTime())
            .set(PAYMENT_OUTBOX_MESSAGES.LAST_ERROR, message.lastError)
            .set(PAYMENT_OUTBOX_MESSAGES.VERSION, message.version)
            .set(PAYMENT_OUTBOX_MESSAGES.CREATED_AT, message.createdAt.toOffsetDateTime())
            .set(PAYMENT_OUTBOX_MESSAGES.UPDATED_AT, message.updatedAt.toOffsetDateTime())
            .execute()

        return findById(message.id) ?: message
    }

    override fun findById(id: String): PaymentOutboxMessage? =
        dsl.selectFrom(PAYMENT_OUTBOX_MESSAGES)
            .where(PAYMENT_OUTBOX_MESSAGES.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    override fun findByPaymentIdAndType(paymentId: String, type: PaymentOutboxType): PaymentOutboxMessage? =
        dsl.selectFrom(PAYMENT_OUTBOX_MESSAGES)
            .where(PAYMENT_OUTBOX_MESSAGES.PAYMENT_ID.eq(paymentId))
            .and(PAYMENT_OUTBOX_MESSAGES.TYPE.eq(type.name))
            .fetchOne()
            ?.toDomain()

    override fun findReadyForProcessing(now: Instant): List<PaymentOutboxMessage> =
        dsl.selectFrom(PAYMENT_OUTBOX_MESSAGES)
            .where(readyCondition(now))
            .orderBy(PAYMENT_OUTBOX_MESSAGES.CREATED_AT.asc(), PAYMENT_OUTBOX_MESSAGES.ID.asc())
            .fetch { record -> record.toDomain() }

    private fun readyCondition(now: Instant): Condition =
        PAYMENT_OUTBOX_MESSAGES.STATUS.eq(PaymentOutboxStatus.PENDING.name)
            .and(
                PAYMENT_OUTBOX_MESSAGES.NEXT_RETRY_AT.isNull
                    .or(PAYMENT_OUTBOX_MESSAGES.NEXT_RETRY_AT.le(now.toOffsetDateTime()))
            )
            .or(
                PAYMENT_OUTBOX_MESSAGES.STATUS.eq(PaymentOutboxStatus.IN_PROGRESS.name)
                    .and(PAYMENT_OUTBOX_MESSAGES.LOCKED_UNTIL.le(now.toOffsetDateTime()))
            )

    private fun PaymentOutboxMessagesRecord.toDomain(): PaymentOutboxMessage =
        PaymentOutboxMessage.reconstitute(
            id = get(PAYMENT_OUTBOX_MESSAGES.ID),
            paymentId = get(PAYMENT_OUTBOX_MESSAGES.PAYMENT_ID),
            reservationId = get(PAYMENT_OUTBOX_MESSAGES.RESERVATION_ID),
            memberId = get(PAYMENT_OUTBOX_MESSAGES.MEMBER_ID),
            type = PaymentOutboxType.valueOf(get(PAYMENT_OUTBOX_MESSAGES.TYPE)),
            paymentKey = get(PAYMENT_OUTBOX_MESSAGES.PAYMENT_KEY),
            orderId = get(PAYMENT_OUTBOX_MESSAGES.ORDER_ID),
            amount = Money.of(get(PAYMENT_OUTBOX_MESSAGES.AMOUNT), get(PAYMENT_OUTBOX_MESSAGES.CURRENCY)),
            cancelReason = get(PAYMENT_OUTBOX_MESSAGES.CANCEL_REASON),
            idempotencyKey = get(PAYMENT_OUTBOX_MESSAGES.IDEMPOTENCY_KEY),
            status = PaymentOutboxStatus.valueOf(get(PAYMENT_OUTBOX_MESSAGES.STATUS)),
            retryCount = get(PAYMENT_OUTBOX_MESSAGES.RETRY_COUNT),
            maxRetries = get(PAYMENT_OUTBOX_MESSAGES.MAX_RETRIES),
            nextRetryAt = get(PAYMENT_OUTBOX_MESSAGES.NEXT_RETRY_AT)?.toInstant(),
            lockedBy = get(PAYMENT_OUTBOX_MESSAGES.LOCKED_BY),
            lockedUntil = get(PAYMENT_OUTBOX_MESSAGES.LOCKED_UNTIL)?.toInstant(),
            lastError = get(PAYMENT_OUTBOX_MESSAGES.LAST_ERROR),
            version = get(PAYMENT_OUTBOX_MESSAGES.VERSION),
            createdAt = get(PAYMENT_OUTBOX_MESSAGES.CREATED_AT).toInstant(),
            updatedAt = get(PAYMENT_OUTBOX_MESSAGES.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
