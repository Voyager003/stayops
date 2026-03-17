package com.stayops.channel.domain.repository

import com.stayops.channel.domain.model.ProcessedWebhookEvent

interface ProcessedWebhookEventRepository {
    fun save(event: ProcessedWebhookEvent): ProcessedWebhookEvent
    fun existsByEventId(eventId: String): Boolean
}
