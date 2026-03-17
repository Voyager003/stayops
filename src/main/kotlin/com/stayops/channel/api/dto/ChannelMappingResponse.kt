package com.stayops.channel.api.dto

import com.stayops.channel.domain.model.ChannelMapping
import com.stayops.channel.domain.model.MappingEntry
import com.stayops.channel.domain.model.MappingType

data class ChannelMappingResponse(
    val propertyId: String,
    val channelCode: String,
    val mappings: List<MappingEntryResponse>
) {
    data class MappingEntryResponse(
        val internalId: String,
        val externalCode: String,
        val type: MappingType
    )

    companion object {
        fun from(mapping: ChannelMapping) = ChannelMappingResponse(
            propertyId = mapping.propertyId,
            channelCode = mapping.channelCode,
            mappings = mapping.mappings.map {
                MappingEntryResponse(it.internalId, it.externalCode, it.type)
            }
        )
    }
}
