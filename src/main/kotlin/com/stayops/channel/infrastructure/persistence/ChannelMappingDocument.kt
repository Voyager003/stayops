package com.stayops.channel.infrastructure.persistence

import com.stayops.channel.domain.model.ChannelMapping
import com.stayops.channel.domain.model.MappingEntry
import com.stayops.channel.domain.model.MappingType
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("channel_mappings")
data class ChannelMappingDocument(
    @Id val id: String,
    val propertyId: String,
    val channelCode: String,
    val mappings: List<MappingEntryData>,
    @Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    data class MappingEntryData(
        val internalId: String,
        val externalCode: String,
        val type: MappingType
    )

    fun toDomain(): ChannelMapping = ChannelMapping.reconstitute(
        id = id,
        propertyId = propertyId,
        channelCode = channelCode,
        mappings = mappings.map { MappingEntry(it.internalId, it.externalCode, it.type) },
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(mapping: ChannelMapping): ChannelMappingDocument = ChannelMappingDocument(
            id = mapping.id,
            propertyId = mapping.propertyId,
            channelCode = mapping.channelCode,
            mappings = mapping.mappings.map { MappingEntryData(it.internalId, it.externalCode, it.type) },
            version = mapping.version,
            createdAt = mapping.createdAt,
            updatedAt = mapping.updatedAt
        )
    }
}
