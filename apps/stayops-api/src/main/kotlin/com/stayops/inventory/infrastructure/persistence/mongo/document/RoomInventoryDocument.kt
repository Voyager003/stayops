package com.stayops.inventory.infrastructure.persistence.mongo.document

import com.stayops.inventory.domain.model.RoomInventory
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.time.LocalDate

@Document("room_inventories")
@CompoundIndex(def = "{'propertyId': 1, 'roomTypeId': 1, 'date': 1}", unique = true)
data class RoomInventoryDocument(
    @Id val id: String,
    val propertyId: String,
    val roomTypeId: String,
    // Stored as "YYYY-MM-DD" string to support range queries ($gte/$lte)
    val date: String,
    val totalCount: Int,
    val reservedCount: Int,
    val blockedCount: Int,
    val heldCount: Int = 0,
    @Version val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toDomain(): RoomInventory = RoomInventory.reconstitute(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        date = LocalDate.parse(date),
        totalCount = totalCount,
        reservedCount = reservedCount,
        blockedCount = blockedCount,
        heldCount = heldCount,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(inventory: RoomInventory): RoomInventoryDocument = RoomInventoryDocument(
            id = inventory.id,
            propertyId = inventory.propertyId,
            roomTypeId = inventory.roomTypeId,
            date = inventory.date.toString(),
            totalCount = inventory.totalCount,
            reservedCount = inventory.reservedCount,
            blockedCount = inventory.blockedCount,
            heldCount = inventory.heldCount,
            version = inventory.version,
            createdAt = inventory.createdAt,
            updatedAt = inventory.updatedAt
        )
    }
}
