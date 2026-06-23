package com.stayops.inventory.application.service

import com.stayops.inventory.application.required.AvailabilitySyncRequester
import com.stayops.inventory.application.provided.RoomInventorySynchronizer
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.domain.IdGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

@Service
class RoomInventorySyncApplication(
    private val inventoryRepository: RoomInventoryRepository,
    private val roomRepository: RoomRepository,
    private val availabilitySyncPort: AvailabilitySyncRequester,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) : RoomInventorySynchronizer {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val INVENTORY_HORIZON_DAYS = 90L
    }

    override fun syncInventoryForRoomType(propertyId: String, roomTypeId: String) {
        val roomCount = roomRepository.findByRoomTypeId(roomTypeId)
            .count { it.propertyId == propertyId }
        if (roomCount < 1) return

        val today = LocalDate.now(clock)
        val endDate = today.plusDays(INVENTORY_HORIZON_DAYS)
        val existing = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
            propertyId, roomTypeId, today, endDate
        ).associateBy { it.date }

        var date = today
        while (!date.isAfter(endDate)) {
            val inventory = existing[date]
            if (inventory == null) {
                val saved = inventoryRepository.save(
                    RoomInventory.create(
                        id = idGenerator.generate(),
                        propertyId = propertyId,
                        roomTypeId = roomTypeId,
                        date = date,
                        totalCount = roomCount
                    )
                )
                availabilitySyncPort.requestAvailabilitySync(propertyId, roomTypeId, saved.date, saved.availableCount)
            } else if (inventory.totalCount != roomCount) {
                val saved = inventoryRepository.save(inventory.updateTotalCount(roomCount))
                availabilitySyncPort.requestAvailabilitySync(propertyId, roomTypeId, saved.date, saved.availableCount)
            }
            date = date.plusDays(1)
        }
        log.info("재고 동기화: propertyId={}, roomTypeId={}", propertyId, roomTypeId)
    }
}
