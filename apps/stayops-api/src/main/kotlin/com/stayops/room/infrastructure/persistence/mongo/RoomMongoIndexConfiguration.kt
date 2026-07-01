package com.stayops.room.infrastructure.persistence.mongo

import com.stayops.shared.config.MongoPersistence
import com.stayops.room.infrastructure.persistence.mongo.document.RoomDocument
import com.stayops.room.infrastructure.persistence.mongo.document.RoomTypeDocument

import jakarta.annotation.PostConstruct
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.stereotype.Component

@MongoPersistence
@Component
class RoomMongoIndexConfiguration(
    private val mongoTemplate: MongoTemplate
) {
    @PostConstruct
    fun createIndexes() {
        mongoTemplate.indexOps(RoomDocument::class.java).createIndex(
            CompoundIndexDefinition(
                org.bson.Document(mapOf("propertyId" to 1, "roomNumber" to 1))
            ).unique()
        )

        mongoTemplate.indexOps(RoomTypeDocument::class.java).createIndex(
            CompoundIndexDefinition(
                org.bson.Document(mapOf("propertyId" to 1, "name" to 1))
            ).unique()
        )
    }
}
