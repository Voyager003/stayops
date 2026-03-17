package com.stayops.channel.api.dto

import com.stayops.channel.domain.model.MappingType

data class CreateMappingRequest(
    val internalId: String,
    val externalCode: String,
    val type: MappingType = MappingType.ROOM_TYPE
)
