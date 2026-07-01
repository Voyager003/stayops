package com.stayops.room.infrastructure.persistence.mongo

import com.stayops.shared.config.MongoPersistence

import com.stayops.room.domain.model.Room
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.infrastructure.persistence.mongo.dao.RoomMongoDao
import com.stayops.room.infrastructure.persistence.mongo.document.RoomDocument
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@MongoPersistence
@Repository
class MongoRoomRepository(
    private val mongo: RoomMongoDao
) : RoomRepository {

    override fun save(room: Room): Room =
        mongo.save(RoomDocument.from(room)).toDomain()

    override fun findById(id: String): Room? =
        mongo.findByIdOrNull(id)?.toDomain()

    override fun findByPropertyId(propertyId: String): List<Room> =
        mongo.findByPropertyId(propertyId).map { it.toDomain() }

    override fun findByRoomTypeId(roomTypeId: String): List<Room> =
        mongo.findByRoomTypeId(roomTypeId).map { it.toDomain() }

    override fun findByPropertyIdAndRoomNumber(propertyId: String, roomNumber: String): Room? =
        mongo.findByPropertyIdAndRoomNumber(propertyId, roomNumber)?.toDomain()
}
