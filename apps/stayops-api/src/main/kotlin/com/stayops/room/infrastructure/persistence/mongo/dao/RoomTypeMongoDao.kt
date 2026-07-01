package com.stayops.room.infrastructure.persistence.mongo.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.room.infrastructure.persistence.mongo.document.RoomTypeDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface RoomTypeMongoDao : MongoRepository<RoomTypeDocument, String> {
    fun findByPropertyId(propertyId: String): List<RoomTypeDocument>
    fun findByPropertyIdAndName(propertyId: String, name: String): RoomTypeDocument?
}
