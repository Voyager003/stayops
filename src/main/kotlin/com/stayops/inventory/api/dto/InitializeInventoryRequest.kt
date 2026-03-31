package com.stayops.inventory.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class OpenInventoryRequest(
    @field:NotBlank val roomTypeId: String,
    @field:NotNull val startDate: LocalDate,
    @field:NotNull val endDate: LocalDate
)
