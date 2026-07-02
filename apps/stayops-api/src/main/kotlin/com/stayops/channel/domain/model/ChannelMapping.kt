package com.stayops.channel.domain.model

import java.time.Instant

/**
 * PMS 내부 식별자와 OTA 외부 식별자를 연결하는 매핑 모델.
 *
 * 현재 Mock OTA는 PMS가 관리하는 roomTypeId를 그대로 수신하므로 별도의 매핑 관리 API를 열지 않는다.
 * 다만 실제 OTA는 채널별 hotel/room/rate code를 사용하므로 ARI push와 webhook 수신에서
 * 내부 ID와 외부 코드를 양방향으로 변환해야 한다.
 *
 * 현재 런타임에서는 webhook 예약 수신 시 외부 roomTypeCode를 내부 roomTypeId로 복원하는 데만 부분 사용한다.
 * PMS에서 Mock OTA로 재고를 push할 때는 roomTypeId를 직접 전송하므로 매핑을 사용하지 않는다.
 */
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
