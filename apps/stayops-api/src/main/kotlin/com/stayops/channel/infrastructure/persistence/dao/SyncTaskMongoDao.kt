package com.stayops.channel.infrastructure.persistence.dao

import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.infrastructure.persistence.SyncTaskDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface SyncTaskMongoDao : MongoRepository<SyncTaskDocument, String> {
    fun findByPropertyIdAndStatus(propertyId: String, status: SyncTaskStatus): List<SyncTaskDocument>
    fun findByPropertyIdAndChannelCodeAndStatus(
        propertyId: String,
        channelCode: String,
        status: SyncTaskStatus
    ): List<SyncTaskDocument>
}
