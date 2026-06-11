package com.stayops.inventory.api.dto

import com.stayops.inventory.domain.model.RoomInventory
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.DayOfWeek
import java.time.LocalDate

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

data class BulkBlockRequest(
    @field:NotNull val startDate: LocalDate,
    @field:NotNull val endDate: LocalDate,
    val daysOfWeek: List<DayOfWeek>? = null,
    @field:NotNull val action: InventoryUpdateAction,
    @field:Min(1) val count: Int = 1
)

data class UpdateInventoryRequest(
    @field:NotNull val action: InventoryUpdateAction,
    @field:Min(1) val count: Int
)

enum class InventoryUpdateAction { BLOCK, UNBLOCK }
