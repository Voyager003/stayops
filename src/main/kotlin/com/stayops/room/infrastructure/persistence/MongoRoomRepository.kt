package com.stayops.room.infrastructure.persistence

import com.stayops.room.domain.model.Room
import com.stayops.room.domain.repository.RoomRepository
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MongoRoomRepository(
    private val mongo: RoomMongoDataRepository
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

interface RoomMongoDataRepository : MongoRepository<RoomDocument, String> {
    fun findByPropertyId(propertyId: String): List<RoomDocument>
    fun findByRoomTypeId(roomTypeId: String): List<RoomDocument>
    fun findByPropertyIdAndRoomNumber(propertyId: String, roomNumber: String): RoomDocument?
}
