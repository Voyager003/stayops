package com.stayops.channel.infrastructure.persistence.mongo.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.channel.infrastructure.persistence.mongo.document.ProcessedWebhookEventDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface ProcessedWebhookEventMongoDao : MongoRepository<ProcessedWebhookEventDocument, String>
