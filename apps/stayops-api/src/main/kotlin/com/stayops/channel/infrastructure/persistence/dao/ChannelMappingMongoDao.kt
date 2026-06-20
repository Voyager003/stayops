package com.stayops.channel.infrastructure.persistence.dao

import com.stayops.channel.infrastructure.persistence.ChannelMappingDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface ChannelMappingMongoDao : MongoRepository<ChannelMappingDocument, String> {
    fun findByPropertyIdAndChannelCode(propertyId: String, channelCode: String): ChannelMappingDocument?
    fun deleteByPropertyIdAndChannelCode(propertyId: String, channelCode: String)
}
