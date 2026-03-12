package com.stayops.room.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreateRoomRequest(
    @field:NotBlank val roomTypeId: String,
    @field:NotBlank val roomNumber: String,
    @field:Min(1) val floor: Int
)
