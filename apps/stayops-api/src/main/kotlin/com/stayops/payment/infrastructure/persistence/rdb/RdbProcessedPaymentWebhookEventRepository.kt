package com.stayops.payment.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.PROCESSED_PAYMENT_WEBHOOK_EVENTS
import com.stayops.payment.domain.model.ProcessedPaymentWebhookEvent
import com.stayops.payment.domain.repository.ProcessedPaymentWebhookEventRepository
import com.stayops.shared.config.RdbPersistence
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbProcessedPaymentWebhookEventRepository(
    private val dsl: DSLContext
) : ProcessedPaymentWebhookEventRepository {

    override fun existsByTransmissionId(transmissionId: String): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(PROCESSED_PAYMENT_WEBHOOK_EVENTS)
                .where(PROCESSED_PAYMENT_WEBHOOK_EVENTS.TRANSMISSION_ID.eq(transmissionId))
        )

    override fun saveIfAbsent(event: ProcessedPaymentWebhookEvent): Boolean {
        if (existsByTransmissionId(event.transmissionId)) return false

        dsl.insertInto(PROCESSED_PAYMENT_WEBHOOK_EVENTS)
            .set(PROCESSED_PAYMENT_WEBHOOK_EVENTS.ID, event.id)
            .set(PROCESSED_PAYMENT_WEBHOOK_EVENTS.TRANSMISSION_ID, event.transmissionId)
            .set(PROCESSED_PAYMENT_WEBHOOK_EVENTS.EVENT_TYPE, event.eventType)
            .set(PROCESSED_PAYMENT_WEBHOOK_EVENTS.PAYMENT_KEY, event.paymentKey)
            .set(PROCESSED_PAYMENT_WEBHOOK_EVENTS.ORDER_ID, event.orderId)
            .set(PROCESSED_PAYMENT_WEBHOOK_EVENTS.PROCESSED_AT, event.processedAt.toOffsetDateTime())
            .execute()

        return true
    }

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
