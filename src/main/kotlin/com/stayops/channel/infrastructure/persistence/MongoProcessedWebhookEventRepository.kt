package com.stayops.channel.infrastructure.persistence

import com.stayops.channel.domain.model.ProcessedWebhookEvent
import com.stayops.channel.domain.repository.ProcessedWebhookEventRepository
import jakarta.annotation.PostConstruct
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class MongoProcessedWebhookEventRepository(
    private val mongo: ProcessedWebhookEventMongoDataRepository,
    private val mongoTemplate: MongoTemplate
) : ProcessedWebhookEventRepository {

    @PostConstruct
    fun createIndexes() {
        val indexOps = mongoTemplate.indexOps(ProcessedWebhookEventDocument::class.java)

        indexOps.createIndex(
            Index().on("eventId", Sort.Direction.ASC).unique()
        )
        indexOps.createIndex(
            Index().on("processedAt", Sort.Direction.ASC).expire(Duration.ofDays(7))
        )
    }

    override fun saveIfAbsent(event: ProcessedWebhookEvent): Boolean =
        try {
            mongo.save(ProcessedWebhookEventDocument.from(event))
            true
        } catch (e: DuplicateKeyException) {
            false
        }
}

interface ProcessedWebhookEventMongoDataRepository : MongoRepository<ProcessedWebhookEventDocument, String>
