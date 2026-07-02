package com.stayops.channel.infrastructure.persistence.mongo.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.channel.infrastructure.persistence.mongo.document.ChannelMappingDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface ChannelMappingMongoDao : MongoRepository<ChannelMappingDocument, String> {
    fun findByPropertyIdAndChannelCode(propertyId: String, channelCode: String): ChannelMappingDocument?
    fun deleteByPropertyIdAndChannelCode(propertyId: String, channelCode: String)
}
