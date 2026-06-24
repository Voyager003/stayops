package com.stayops.inventory.infrastructure.persistence

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.inventory.infrastructure.persistence.dao.RoomInventoryMongoDao
import com.stayops.shared.exception.ConflictException
import jakarta.annotation.PostConstruct
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class MongoRoomInventoryRepository(
    private val mongo: RoomInventoryMongoDao,
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
        try {
            mongo.save(RoomInventoryDocument.from(inventory)).toDomain()
        } catch (e: OptimisticLockingFailureException) {
            throw ConflictException(
                code = "INVENTORY_CONFLICT",
                message = "재고 변경 충돌이 발생했습니다. 다시 시도해주세요."
            )
        }

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
