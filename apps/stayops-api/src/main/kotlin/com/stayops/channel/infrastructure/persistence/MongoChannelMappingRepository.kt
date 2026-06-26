package com.stayops.channel.infrastructure.persistence

import com.stayops.shared.config.MongoPersistence

import com.stayops.channel.domain.model.ChannelMapping
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.channel.infrastructure.persistence.dao.ChannelMappingMongoDao
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.stereotype.Repository

@MongoPersistence
@Repository
class MongoChannelMappingRepository(
    private val mongo: ChannelMappingMongoDao,
    private val mongoTemplate: MongoTemplate
) : ChannelMappingRepository {

    @PostConstruct
    fun createIndexes() {
        mongoTemplate.indexOps(ChannelMappingDocument::class.java).createIndex(
            CompoundIndexDefinition(Document(mapOf("propertyId" to 1, "channelCode" to 1))).unique()
        )
    }

    override fun save(mapping: ChannelMapping): ChannelMapping =
        mongo.save(ChannelMappingDocument.from(mapping)).toDomain()

    override fun findByPropertyIdAndChannelCode(propertyId: String, channelCode: String): ChannelMapping? =
        mongo.findByPropertyIdAndChannelCode(propertyId, channelCode)?.toDomain()

    override fun deleteByPropertyIdAndChannelCode(propertyId: String, channelCode: String) {
        mongo.deleteByPropertyIdAndChannelCode(propertyId, channelCode)
    }
}
