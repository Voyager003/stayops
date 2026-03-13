package com.stayops.channel.infrastructure.persistence

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.repository.ChannelRepository
import jakarta.annotation.PostConstruct
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
class MongoChannelRepository(
    private val mongo: ChannelMongoDataRepository,
    private val mongoTemplate: MongoTemplate
) : ChannelRepository {

    @PostConstruct
    fun createIndexes() {
        val indexOps = mongoTemplate.indexOps(ChannelDocument::class.java)
        indexOps.createIndex(
            CompoundIndexDefinition(
                org.bson.Document(mapOf("propertyId" to 1, "code" to 1))
            ).unique()
        )
        indexOps.createIndex(
            CompoundIndexDefinition(
                org.bson.Document(mapOf("propertyId" to 1, "status" to 1))
            )
        )
    }

    override fun save(channel: Channel): Channel =
        mongo.save(ChannelDocument.from(channel)).toDomain()

    override fun findById(id: String): Channel? =
        mongo.findById(id).orElse(null)?.toDomain()

    override fun findByPropertyId(propertyId: String): List<Channel> =
        mongo.findByPropertyId(propertyId).map { it.toDomain() }

    override fun findByPropertyIdAndCode(propertyId: String, code: String): Channel? =
        mongo.findByPropertyIdAndCode(propertyId, code)?.toDomain()

    override fun findByPropertyIdAndStatus(propertyId: String, status: ChannelStatus): List<Channel> =
        mongo.findByPropertyIdAndStatus(propertyId, status).map { it.toDomain() }

    override fun deleteById(id: String) = mongo.deleteById(id)
}

interface ChannelMongoDataRepository : MongoRepository<ChannelDocument, String> {
    fun findByPropertyId(propertyId: String): List<ChannelDocument>
    fun findByPropertyIdAndCode(propertyId: String, code: String): ChannelDocument?
    fun findByPropertyIdAndStatus(propertyId: String, status: ChannelStatus): List<ChannelDocument>
}
