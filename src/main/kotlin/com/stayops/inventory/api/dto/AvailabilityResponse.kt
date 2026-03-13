package com.stayops.inventory.api.dto

import com.stayops.inventory.domain.model.RoomInventory

data class AvailabilityResponse(
    val date: String,
    val totalCount: Int,
    val reservedCount: Int,
    val blockedCount: Int,
    val availableCount: Int
) {
    companion object {
        fun from(inventory: RoomInventory) = AvailabilityResponse(
            date = inventory.date.toString(),
            totalCount = inventory.totalCount,
            reservedCount = inventory.reservedCount,
            blockedCount = inventory.blockedCount,
            availableCount = inventory.availableCount
        )
    }
}
