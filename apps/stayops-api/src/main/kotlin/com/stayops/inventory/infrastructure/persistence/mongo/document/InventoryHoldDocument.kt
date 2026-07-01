package com.stayops.inventory.infrastructure.persistence.mongo.document

import com.stayops.inventory.domain.model.InventoryHold
import com.stayops.inventory.domain.model.InventoryHoldStatus
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.time.LocalDate

@Document("inventory_holds")
data class InventoryHoldDocument(
    @Id val id: String,
    val reservationIntentId: String,
    val propertyId: String,
    val roomTypeId: String,
    val dates: List<String>,
    val quantity: Int,
    val status: InventoryHoldStatus,
    val expiresAt: Instant,
    @Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toDomain(): InventoryHold = InventoryHold.reconstitute(
        id = id,
        reservationIntentId = reservationIntentId,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        dates = dates.map { LocalDate.parse(it) },
        quantity = quantity,
        status = status,
        expiresAt = expiresAt,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(hold: InventoryHold): InventoryHoldDocument = InventoryHoldDocument(
            id = hold.id,
            reservationIntentId = hold.reservationIntentId,
            propertyId = hold.propertyId,
            roomTypeId = hold.roomTypeId,
            dates = hold.dates.map { it.toString() },
            quantity = hold.quantity,
            status = hold.status,
            expiresAt = hold.expiresAt,
            version = hold.version,
            createdAt = hold.createdAt,
            updatedAt = hold.updatedAt
        )
    }
}
