package com.stayops.room.infrastructure.persistence.dao

import com.stayops.room.infrastructure.persistence.RoomTypeDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface RoomTypeMongoDao : MongoRepository<RoomTypeDocument, String> {
    fun findByPropertyId(propertyId: String): List<RoomTypeDocument>
    fun findByPropertyIdAndName(propertyId: String, name: String): RoomTypeDocument?
}
