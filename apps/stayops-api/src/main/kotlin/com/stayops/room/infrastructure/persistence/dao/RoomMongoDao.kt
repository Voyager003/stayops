package com.stayops.room.infrastructure.persistence.dao

import com.stayops.room.infrastructure.persistence.RoomDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface RoomMongoDao : MongoRepository<RoomDocument, String> {
    fun findByPropertyId(propertyId: String): List<RoomDocument>
    fun findByRoomTypeId(roomTypeId: String): List<RoomDocument>
    fun findByPropertyIdAndRoomNumber(propertyId: String, roomNumber: String): RoomDocument?
}
