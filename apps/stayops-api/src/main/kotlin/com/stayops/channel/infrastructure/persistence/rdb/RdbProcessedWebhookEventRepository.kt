package com.stayops.channel.infrastructure.persistence.rdb

import com.stayops.channel.domain.model.ProcessedWebhookEvent
import com.stayops.channel.domain.repository.ProcessedWebhookEventRepository
import com.stayops.jooq.generated.Tables.PROCESSED_WEBHOOK_EVENTS
import com.stayops.shared.config.RdbPersistence
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbProcessedWebhookEventRepository(
    private val dsl: DSLContext
) : ProcessedWebhookEventRepository {

    override fun saveIfAbsent(event: ProcessedWebhookEvent): Boolean {
        val exists = dsl.fetchExists(
            dsl.selectOne()
                .from(PROCESSED_WEBHOOK_EVENTS)
                .where(PROCESSED_WEBHOOK_EVENTS.EVENT_ID.eq(event.eventId))
        )

        if (exists) return false

        dsl.insertInto(PROCESSED_WEBHOOK_EVENTS)
            .set(PROCESSED_WEBHOOK_EVENTS.ID, event.id)
            .set(PROCESSED_WEBHOOK_EVENTS.EVENT_ID, event.eventId)
            .set(PROCESSED_WEBHOOK_EVENTS.CHANNEL_CODE, event.channelCode)
            .set(PROCESSED_WEBHOOK_EVENTS.PROPERTY_ID, event.propertyId)
            .set(PROCESSED_WEBHOOK_EVENTS.PROCESSED_AT, event.processedAt.toOffsetDateTime())
            .execute()

        return true
    }

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
