package com.stayops.room.infrastructure.persistence

import jakarta.annotation.PostConstruct
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.stereotype.Component

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
