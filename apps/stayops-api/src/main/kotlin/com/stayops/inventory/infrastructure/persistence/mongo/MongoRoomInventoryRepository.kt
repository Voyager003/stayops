package com.stayops.inventory.infrastructure.persistence.mongo

import com.stayops.shared.config.MongoPersistence

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.inventory.infrastructure.persistence.mongo.dao.RoomInventoryMongoDao
import com.stayops.inventory.infrastructure.persistence.mongo.document.RoomInventoryDocument
import com.stayops.shared.exception.ConflictException
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.stereotype.Repository
import java.time.LocalDate

@MongoPersistence
@Repository
class MongoRoomInventoryRepository(
    private val mongo: RoomInventoryMongoDao,
    private val mongoTemplate: MongoTemplate
) : RoomInventoryRepository {

    private val log = LoggerFactory.getLogger(javaClass)

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
            mongo.save(RoomInventoryDocument.from(inventory)).toDomain().also { saved ->
                log.debug(
                    "[재고 저장] inventoryId={}, date={}, version={}->{}, held={}, reserved={}, available={}",
                    saved.id,
                    saved.date,
                    inventory.version,
                    saved.version,
                    saved.heldCount,
                    saved.reservedCount,
                    saved.availableCount
                )
            }
        } catch (e: OptimisticLockingFailureException) {
            // attemptedVersion identifies which snapshot lost the race
            log.warn(
                "[재고 낙관적 락 충돌] inventoryId={}, propertyId={}, roomTypeId={}, date={}, attemptedVersion={}",
                inventory.id,
                inventory.propertyId,
                inventory.roomTypeId,
                inventory.date,
                inventory.version
            )
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
