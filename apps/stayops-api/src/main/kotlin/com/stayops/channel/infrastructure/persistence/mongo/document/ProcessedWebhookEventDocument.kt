package com.stayops.channel.infrastructure.persistence.mongo.document

import com.stayops.channel.domain.model.ProcessedWebhookEvent
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("processed_webhook_events")
data class ProcessedWebhookEventDocument(
    @Id val id: String,
    val eventId: String,
    val channelCode: String,
    val propertyId: String,
    val processedAt: Instant
) {

    fun toDomain(): ProcessedWebhookEvent = ProcessedWebhookEvent(
        id = id,
        eventId = eventId,
        channelCode = channelCode,
        propertyId = propertyId,
        processedAt = processedAt
    )

    companion object {
        fun from(event: ProcessedWebhookEvent): ProcessedWebhookEventDocument =
            ProcessedWebhookEventDocument(
                id = event.id,
                eventId = event.eventId,
                channelCode = event.channelCode,
                propertyId = event.propertyId,
                processedAt = event.processedAt
            )
    }
}
