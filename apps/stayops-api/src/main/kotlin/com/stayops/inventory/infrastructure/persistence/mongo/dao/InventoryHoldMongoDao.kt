package com.stayops.inventory.infrastructure.persistence.mongo.dao

import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.infrastructure.persistence.mongo.document.InventoryHoldDocument
import com.stayops.shared.config.MongoPersistence
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

@MongoPersistence
interface InventoryHoldMongoDao : MongoRepository<InventoryHoldDocument, String> {
    fun findByReservationIntentId(reservationIntentId: String): InventoryHoldDocument?

    fun findByPropertyIdAndRoomTypeIdAndStatusInAndExpiresAtGreaterThanEqual(
        propertyId: String,
        roomTypeId: String,
        statuses: Collection<InventoryHoldStatus>,
        expiresAt: Instant
    ): List<InventoryHoldDocument>
}
