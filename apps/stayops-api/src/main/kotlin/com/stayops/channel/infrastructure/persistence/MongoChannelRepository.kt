package com.stayops.channel.infrastructure.persistence

import com.stayops.shared.config.MongoPersistence

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.infrastructure.persistence.dao.ChannelMongoDao
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@MongoPersistence
@Repository
class MongoChannelRepository(
    private val mongo: ChannelMongoDao,
    private val mongoTemplate: MongoTemplate
) : ChannelRepository {

    @PostConstruct
    fun createIndexes() {
        val indexOps = mongoTemplate.indexOps(ChannelDocument::class.java)

        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("propertyId" to 1, "code" to 1))).unique()
        )
        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("propertyId" to 1, "status" to 1)))
        )
    }

    override fun save(channel: Channel): Channel =
        mongo.save(ChannelDocument.from(channel)).toDomain()

    override fun findById(id: String): Channel? =
        mongo.findByIdOrNull(id)?.toDomain()

    override fun findByPropertyId(propertyId: String): List<Channel> =
        mongo.findByPropertyId(propertyId).map { it.toDomain() }

    override fun findByPropertyIdAndCode(propertyId: String, code: String): Channel? =
        mongo.findByPropertyIdAndCode(propertyId, code)?.toDomain()

    override fun findByPropertyIdAndStatus(propertyId: String, status: ChannelStatus): List<Channel> =
        mongo.findByPropertyIdAndStatus(propertyId, status).map { it.toDomain() }

    override fun deleteById(id: String) =
        mongo.deleteById(id)
}
