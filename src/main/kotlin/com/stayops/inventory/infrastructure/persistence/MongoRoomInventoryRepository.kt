package com.stayops.inventory.infrastructure.persistence

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import jakarta.annotation.PostConstruct
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class MongoRoomInventoryRepository(
    private val mongo: RoomInventoryMongoDataRepository,
    private val mongoTemplate: MongoTemplate
) : RoomInventoryRepository {

    @PostConstruct
    fun createIndexes() {
        mongoTemplate.indexOps(RoomInventoryDocument::class.java).createIndex(
            CompoundIndexDefinition(
                org.bson.Document(mapOf("propertyId" to 1, "roomTypeId" to 1, "date" to 1))
            ).unique()
        )
    }

    override fun save(inventory: RoomInventory): RoomInventory =
        mongo.save(RoomInventoryDocument.from(inventory)).toDomain()

    override fun findByPropertyIdAndRoomTypeIdAndDate(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate
    ): RoomInventory? =
        mongo.findByPropertyIdAndRoomTypeIdAndDate(propertyId, roomTypeId, date.toString())?.toDomain()

    override fun findByPropertyIdAndRoomTypeIdAndDateBetween(
        propertyId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RoomInventory> =
        mongo.findByPropertyIdAndRoomTypeIdAndDateBetween(propertyId, roomTypeId, startDate.toString(), endDate.toString())
            .map { it.toDomain() }

}

interface RoomInventoryMongoDataRepository : MongoRepository<RoomInventoryDocument, String> {
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
