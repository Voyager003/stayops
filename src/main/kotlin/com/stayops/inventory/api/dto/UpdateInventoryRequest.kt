package com.stayops.inventory.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class UpdateInventoryRequest(
    @field:NotNull val action: InventoryUpdateAction,
    @field:Min(1) val count: Int
)

enum class InventoryUpdateAction { BLOCK, UNBLOCK }
