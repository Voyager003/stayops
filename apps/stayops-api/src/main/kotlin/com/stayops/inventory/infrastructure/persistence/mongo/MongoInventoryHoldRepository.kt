package com.stayops.inventory.infrastructure.persistence.mongo

import com.stayops.inventory.domain.model.InventoryHold
import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.inventory.infrastructure.persistence.mongo.dao.InventoryHoldMongoDao
import com.stayops.inventory.infrastructure.persistence.mongo.document.InventoryHoldDocument
import com.stayops.shared.config.MongoPersistence
import jakarta.annotation.PostConstruct
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@MongoPersistence
@Repository
class MongoInventoryHoldRepository(
    private val mongo: InventoryHoldMongoDao,
    private val mongoTemplate: MongoTemplate
) : InventoryHoldRepository {

    @PostConstruct
    fun createIndexes() {
        mongoTemplate.indexOps(InventoryHoldDocument::class.java)
            .createIndex(Index().on("reservationIntentId", Sort.Direction.ASC).unique())
    }

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
