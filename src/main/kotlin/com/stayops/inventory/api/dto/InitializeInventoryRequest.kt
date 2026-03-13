package com.stayops.inventory.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class InitializeInventoryRequest(
    @field:NotBlank val roomTypeId: String,
    @field:NotNull val date: LocalDate,
    @field:Min(1) val totalCount: Int
)
