package com.stayops.channel.domain.repository

import com.stayops.channel.domain.model.ProcessedWebhookEvent

interface ProcessedWebhookEventRepository {
    /**
     * 멱등 저장: 동일한 eventId가 이미 존재하면 false를 반환하고 저장하지 않는다.
     * Webhook 중복 수신에 대한 멱등성 처리를 위해 사용한다.
     */
    fun saveIfAbsent(event: ProcessedWebhookEvent): Boolean
}
