package com.stayops.payment.domain.repository

import com.stayops.payment.domain.model.ProcessedPaymentWebhookEvent

interface ProcessedPaymentWebhookEventRepository {
    fun existsByTransmissionId(transmissionId: String): Boolean
    fun saveIfAbsent(event: ProcessedPaymentWebhookEvent): Boolean
}
