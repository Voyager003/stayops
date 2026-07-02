package com.stayops.room.infrastructure.persistence.mongo.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.room.infrastructure.persistence.mongo.document.RoomDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface RoomMongoDao : MongoRepository<RoomDocument, String> {
    fun findByPropertyId(propertyId: String): List<RoomDocument>
    fun findByRoomTypeId(roomTypeId: String): List<RoomDocument>
    fun findByPropertyIdAndRoomNumber(propertyId: String, roomNumber: String): RoomDocument?
}
