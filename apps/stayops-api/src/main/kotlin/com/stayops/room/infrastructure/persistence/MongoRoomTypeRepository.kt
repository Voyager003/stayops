package com.stayops.room.infrastructure.persistence

import com.stayops.shared.config.MongoPersistence

import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.room.infrastructure.persistence.dao.RoomTypeMongoDao
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@MongoPersistence
@Repository
class MongoRoomTypeRepository(
    private val mongo: RoomTypeMongoDao
) : RoomTypeRepository {

    override fun save(roomType: RoomType): RoomType =
        mongo.save(RoomTypeDocument.from(roomType)).toDomain()

    override fun findById(id: String): RoomType? =
        mongo.findByIdOrNull(id)?.toDomain()

    override fun findByPropertyId(propertyId: String): List<RoomType> =
        mongo.findByPropertyId(propertyId).map { it.toDomain() }

    override fun findByPropertyIdAndName(propertyId: String, name: String): RoomType? =
        mongo.findByPropertyIdAndName(propertyId, name)?.toDomain()

    override fun deleteById(id: String) {
        mongo.deleteById(id)
    }
}
