package com.stayops.inventory.domain.repository

import com.stayops.inventory.domain.model.RoomInventory
import java.time.LocalDate

interface RoomInventoryRepository {
    fun save(inventory: RoomInventory): RoomInventory
    fun findByPropertyIdAndRoomTypeIdAndDate(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate
    ): RoomInventory?
    fun findByPropertyIdAndRoomTypeIdAndDateBetween(
        propertyId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RoomInventory>
}
