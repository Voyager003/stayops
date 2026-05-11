package com.stayops.payment.infrastructure.persistence

import com.stayops.payment.domain.model.ProcessedPaymentWebhookEvent
import com.stayops.payment.domain.repository.ProcessedPaymentWebhookEventRepository
import jakarta.annotation.PostConstruct
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class MongoProcessedPaymentWebhookEventRepository(
    private val mongo: ProcessedPaymentWebhookEventMongoDataRepository,
    private val mongoTemplate: MongoTemplate
) : ProcessedPaymentWebhookEventRepository {

    @PostConstruct
    fun createIndexes() {
        val indexOps = mongoTemplate.indexOps(ProcessedPaymentWebhookEventDocument::class.java)
        indexOps.createIndex(
            Index().on("transmissionId", Sort.Direction.ASC).unique()
        )
        indexOps.createIndex(
            Index().on("processedAt", Sort.Direction.ASC).expire(Duration.ofDays(7))
        )
    }

    override fun existsByTransmissionId(transmissionId: String): Boolean =
        mongo.existsByTransmissionId(transmissionId)

    override fun saveIfAbsent(event: ProcessedPaymentWebhookEvent): Boolean =
        try {
            mongo.save(ProcessedPaymentWebhookEventDocument.from(event))
            true
        } catch (e: DuplicateKeyException) {
            false
        }
}

interface ProcessedPaymentWebhookEventMongoDataRepository : MongoRepository<ProcessedPaymentWebhookEventDocument, String> {
    fun existsByTransmissionId(transmissionId: String): Boolean
}
