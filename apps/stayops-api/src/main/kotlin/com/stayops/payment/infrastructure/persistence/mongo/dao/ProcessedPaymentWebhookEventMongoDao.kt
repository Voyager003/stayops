package com.stayops.payment.infrastructure.persistence.mongo.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.payment.infrastructure.persistence.mongo.document.ProcessedPaymentWebhookEventDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface ProcessedPaymentWebhookEventMongoDao : MongoRepository<ProcessedPaymentWebhookEventDocument, String> {
    fun existsByTransmissionId(transmissionId: String): Boolean
}
