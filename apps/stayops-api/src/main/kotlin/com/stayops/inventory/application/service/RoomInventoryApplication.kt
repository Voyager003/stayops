package com.stayops.inventory.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate

@Service
class RoomInventoryApplication(
    private val inventoryRepository: RoomInventoryRepository,
    private val syncApplication: RoomInventorySyncApplication,
    private val managementApplication: RoomInventoryManagementApplication
) {
    fun syncInventoryForRoomType(propertyId: String, roomTypeId: String) {
        syncApplication.syncInventoryForRoomType(propertyId, roomTypeId)
    }

    fun getAvailability(
        propertyId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RoomInventory> =
        inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(propertyId, roomTypeId, startDate, endDate)

    fun bulkBlock(
        propertyId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        daysOfWeek: List<DayOfWeek>?,
        action: String,
        count: Int
    ): Int =
        managementApplication.bulkBlock(propertyId, roomTypeId, startDate, endDate, daysOfWeek, action, count)

    fun blockInventory(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        count: Int
    ): RoomInventory =
        managementApplication.blockInventory(propertyId, roomTypeId, date, count)

    fun unblockInventory(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        count: Int
    ): RoomInventory =
        managementApplication.unblockInventory(propertyId, roomTypeId, date, count)
}
