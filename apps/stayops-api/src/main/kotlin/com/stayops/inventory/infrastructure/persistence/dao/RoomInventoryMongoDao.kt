package com.stayops.inventory.infrastructure.persistence.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.inventory.infrastructure.persistence.RoomInventoryDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query




@MongoPersistence
interface RoomInventoryMongoDao : MongoRepository<RoomInventoryDocument, String> {
    fun findByPropertyIdAndRoomTypeIdAndDate(
        propertyId: String,
        roomTypeId: String,
        date: String
    ): RoomInventoryDocument?

    @Query("{ 'propertyId': ?0, 'roomTypeId': ?1, 'date': { '\$gte': ?2, '\$lte': ?3 } }")
    fun findByPropertyIdAndRoomTypeIdAndDateBetween(
        propertyId: String,
        roomTypeId: String,
        startDate: String,
        endDate: String
    ): List<RoomInventoryDocument>
}
