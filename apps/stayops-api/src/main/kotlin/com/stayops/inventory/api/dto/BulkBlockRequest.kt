package com.stayops.inventory.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.time.DayOfWeek
import java.time.LocalDate

data class BulkBlockRequest(
    @field:NotNull val startDate: LocalDate,
    @field:NotNull val endDate: LocalDate,
    val daysOfWeek: List<DayOfWeek>? = null,
    @field:NotNull val action: InventoryUpdateAction,
    @field:Min(1) val count: Int = 1
)
