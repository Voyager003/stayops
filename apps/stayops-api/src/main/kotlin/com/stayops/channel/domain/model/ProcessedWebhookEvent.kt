package com.stayops.channel.domain.model

import java.time.Instant

data class ProcessedWebhookEvent(
    val id: String,
    val eventId: String,
    val channelCode: String,
    val propertyId: String,
    val processedAt: Instant = Instant.now()
)
