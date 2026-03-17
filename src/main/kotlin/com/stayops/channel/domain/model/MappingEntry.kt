package com.stayops.channel.domain.model

data class MappingEntry(
    val internalId: String,
    val externalCode: String,
    val type: MappingType
)
