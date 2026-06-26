package com.stayops.channel.infrastructure.persistence.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.infrastructure.persistence.ChannelDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface ChannelMongoDao : MongoRepository<ChannelDocument, String> {
    fun findByPropertyId(propertyId: String): List<ChannelDocument>
    fun findByPropertyIdAndCode(propertyId: String, code: String): ChannelDocument?
    fun findByPropertyIdAndStatus(propertyId: String, status: ChannelStatus): List<ChannelDocument>
}
