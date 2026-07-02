package com.stayops.payment.infrastructure.persistence.mongo.document

import com.stayops.payment.domain.model.ProcessedPaymentWebhookEvent
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("processed_payment_webhook_events")
data class ProcessedPaymentWebhookEventDocument(
    @Id val id: String,
    val transmissionId: String,
    val eventType: String,
    val paymentKey: String,
    val orderId: String,
    val processedAt: Instant
) {

    fun toDomain(): ProcessedPaymentWebhookEvent = ProcessedPaymentWebhookEvent(
        id = id,
        transmissionId = transmissionId,
        eventType = eventType,
        paymentKey = paymentKey,
        orderId = orderId,
        processedAt = processedAt
    )

    companion object {
        fun from(event: ProcessedPaymentWebhookEvent): ProcessedPaymentWebhookEventDocument =
            ProcessedPaymentWebhookEventDocument(
                id = event.id,
                transmissionId = event.transmissionId,
                eventType = event.eventType,
                paymentKey = event.paymentKey,
                orderId = event.orderId,
                processedAt = event.processedAt
            )
    }
}
