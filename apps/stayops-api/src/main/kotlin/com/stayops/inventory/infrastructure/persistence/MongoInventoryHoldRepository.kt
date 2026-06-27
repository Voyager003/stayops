package com.stayops.inventory.infrastructure.persistence

import com.stayops.inventory.domain.model.InventoryHold
import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.inventory.infrastructure.persistence.dao.InventoryHoldMongoDao
import com.stayops.shared.config.MongoPersistence
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@MongoPersistence
@Repository
class MongoInventoryHoldRepository(
    private val mongo: InventoryHoldMongoDao
) : InventoryHoldRepository {

    override fun save(hold: InventoryHold): InventoryHold =
        mongo.save(InventoryHoldDocument.from(hold)).toDomain()

    override fun findById(id: String): InventoryHold? =
        mongo.findById(id).orElse(null)?.toDomain()

    override fun findByReservationIntentId(reservationIntentId: String): InventoryHold? =
        mongo.findByReservationIntentId(reservationIntentId)?.toDomain()

    override fun findActiveByPropertyIdAndRoomTypeIdAndDates(
        propertyId: String,
        roomTypeId: String,
        dates: List<LocalDate>,
        now: Instant
    ): List<InventoryHold> {
        if (dates.isEmpty()) return emptyList()
        val requestedDates = dates.toSet()
        return mongo.findByPropertyIdAndRoomTypeIdAndStatusInAndExpiresAtGreaterThanEqual(
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            statuses = activeStatuses,
            expiresAt = now
        )
            .map { it.toDomain() }
            .filter { hold -> hold.dates.any { it in requestedDates } }
    }

    companion object {
        private val activeStatuses = listOf(
            InventoryHoldStatus.HELD,
            InventoryHoldStatus.PAYMENT_PROCESSING
        )
    }
}
