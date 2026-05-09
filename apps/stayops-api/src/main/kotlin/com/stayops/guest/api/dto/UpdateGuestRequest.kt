package com.stayops.guest.api.dto

import jakarta.validation.constraints.NotBlank

data class UpdateGuestRequest(
    @field:NotBlank val name: String,
    val memo: String? = null
)
