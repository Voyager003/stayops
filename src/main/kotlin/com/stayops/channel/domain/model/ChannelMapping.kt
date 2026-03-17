package com.stayops.channel.domain.model

import java.time.Instant

@ConsistentCopyVisibility
data class ChannelMapping private constructor(
    val id: String,
    val propertyId: String,
    val channelCode: String,
    val mappings: List<MappingEntry>,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    fun addMapping(entry: MappingEntry): ChannelMapping {
        require(mappings.none { it.internalId == entry.internalId && it.type == entry.type }) {
            "이미 매핑된 internalId입니다: ${entry.internalId} (${entry.type})"
        }
        require(mappings.none { it.externalCode == entry.externalCode && it.type == entry.type }) {
            "이미 매핑된 externalCode입니다: ${entry.externalCode} (${entry.type})"
        }
        return copy(mappings = mappings + entry, updatedAt = Instant.now())
    }

    fun removeMapping(internalId: String, type: MappingType): ChannelMapping {
        require(mappings.any { it.internalId == internalId && it.type == type }) {
            "존재하지 않는 매핑입니다: $internalId ($type)"
        }
        return copy(
            mappings = mappings.filter { !(it.internalId == internalId && it.type == type) },
            updatedAt = Instant.now()
        )
    }

    fun findExternalCode(internalId: String, type: MappingType): String? =
        mappings.find { it.internalId == internalId && it.type == type }?.externalCode

    fun findInternalId(externalCode: String, type: MappingType): String? =
        mappings.find { it.externalCode == externalCode && it.type == type }?.internalId

    companion object {
        fun create(id: String, propertyId: String, channelCode: String): ChannelMapping {
            val now = Instant.now()
            return ChannelMapping(
                id = id,
                propertyId = propertyId,
                channelCode = channelCode,
                mappings = emptyList(),
                version = 0L,
                createdAt = now,
                updatedAt = now
            )
        }

        fun reconstitute(
            id: String,
            propertyId: String,
            channelCode: String,
            mappings: List<MappingEntry>,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): ChannelMapping = ChannelMapping(
            id = id,
            propertyId = propertyId,
            channelCode = channelCode,
            mappings = mappings,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
