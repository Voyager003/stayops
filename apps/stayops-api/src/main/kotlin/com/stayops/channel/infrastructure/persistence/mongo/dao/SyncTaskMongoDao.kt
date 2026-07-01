package com.stayops.channel.infrastructure.persistence.mongo.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.infrastructure.persistence.mongo.document.SyncTaskDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface SyncTaskMongoDao : MongoRepository<SyncTaskDocument, String> {
    fun findByPropertyIdAndStatus(propertyId: String, status: SyncTaskStatus): List<SyncTaskDocument>
    fun findByPropertyIdAndChannelCodeAndStatus(
        propertyId: String,
        channelCode: String,
        status: SyncTaskStatus
    ): List<SyncTaskDocument>
}
