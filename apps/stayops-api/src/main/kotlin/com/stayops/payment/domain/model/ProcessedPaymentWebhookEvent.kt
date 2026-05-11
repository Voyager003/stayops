package com.stayops.payment.domain.model

import java.time.Instant

data class ProcessedPaymentWebhookEvent(
    val id: String,
    val transmissionId: String,
    val eventType: String,
    val paymentKey: String,
    val orderId: String,
    val processedAt: Instant = Instant.now()
)
