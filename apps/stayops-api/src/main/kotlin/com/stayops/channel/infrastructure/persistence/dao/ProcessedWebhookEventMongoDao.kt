package com.stayops.channel.infrastructure.persistence.dao

import com.stayops.channel.infrastructure.persistence.ProcessedWebhookEventDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface ProcessedWebhookEventMongoDao : MongoRepository<ProcessedWebhookEventDocument, String>
