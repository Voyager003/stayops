package com.stayops.channel.api.dto

import com.stayops.channel.domain.model.MappingType
import jakarta.validation.constraints.NotBlank

data class CreateMappingRequest(
    @field:NotBlank
    val internalId: String,
    @field:NotBlank
    val externalCode: String,
    val type: MappingType = MappingType.ROOM_TYPE
)
