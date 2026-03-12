package com.stayops.room.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class CreateRoomTypeRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val description: String,
    @field:Min(1) val maxOccupancy: Int,
    @field:Positive val basePrice: BigDecimal,
    val currency: String = "KRW",
    val amenities: List<String> = emptyList()
)
