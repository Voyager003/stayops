package com.stayops.inventory.application.required

import com.stayops.inventory.domain.model.RoomInventory
import java.time.LocalDate

interface RoomInventoryCache {
    fun get(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory?

    fun put(inventory: RoomInventory)

    fun evict(propertyId: String, roomTypeId: String, date: LocalDate)
}
