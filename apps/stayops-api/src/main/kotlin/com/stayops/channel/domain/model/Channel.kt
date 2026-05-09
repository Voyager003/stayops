package com.stayops.channel.domain.model

import java.math.BigDecimal
import java.time.Instant

@ConsistentCopyVisibility
data class Channel private constructor(
    val id: String,
    val propertyId: String,
    val code: String,
    val name: String,
    val type: ChannelType,
    val commissionRate: BigDecimal,
    val connectionInfo: ChannelConnectionInfo?,
    val status: ChannelStatus,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    fun activate(): Channel {
        check(status != ChannelStatus.ACTIVE) {
            "이미 ACTIVE 상태입니다: $code"
        }
        return copy(status = ChannelStatus.ACTIVE, updatedAt = Instant.now())
    }

    fun deactivate(): Channel {
        check(status == ChannelStatus.ACTIVE) {
            "ACTIVE 상태에서만 비활성화할 수 있습니다: $status"
        }
        return copy(status = ChannelStatus.INACTIVE, updatedAt = Instant.now())
    }

    fun suspend(): Channel {
        check(status == ChannelStatus.ACTIVE) {
            "ACTIVE 상태에서만 정지할 수 있습니다: $status"
        }
        return copy(status = ChannelStatus.SUSPENDED, updatedAt = Instant.now())
    }

    companion object {
        private const val DIRECT_CODE = "DIRECT"
        private const val DIRECT_NAME = "자사 예매 사이트"

        fun createDirect(id: String, propertyId: String): Channel {
            val now = Instant.now()
            return Channel(
                id = id,
                propertyId = propertyId,
                code = DIRECT_CODE,
                name = DIRECT_NAME,
                type = ChannelType.DIRECT,
                commissionRate = BigDecimal.ZERO,
                connectionInfo = null,
                status = ChannelStatus.ACTIVE,
                version = 0L,
                createdAt = now,
                updatedAt = now
            )
        }

        fun createOta(
            id: String,
            propertyId: String,
            code: String,
            name: String,
            commissionRate: BigDecimal,
            apiEndpoint: String
        ): Channel {
            require(code.isNotBlank()) { "채널 코드는 공백일 수 없습니다." }
            require(name.isNotBlank()) { "채널 이름은 공백일 수 없습니다." }
            require(commissionRate > BigDecimal.ZERO) { "OTA 수수료율은 0보다 커야 합니다: $commissionRate" }
            require(commissionRate <= BigDecimal.ONE) { "수수료율은 1 이하여야 합니다: $commissionRate" }

            val now = Instant.now()
            return Channel(
                id = id,
                propertyId = propertyId,
                code = code,
                name = name,
                type = ChannelType.OTA,
                commissionRate = commissionRate,
                connectionInfo = ChannelConnectionInfo(apiEndpoint = apiEndpoint),
                status = ChannelStatus.ACTIVE,
                version = 0L,
                createdAt = now,
                updatedAt = now
            )
        }

        fun reconstitute(
            id: String,
            propertyId: String,
            code: String,
            name: String,
            type: ChannelType,
            commissionRate: BigDecimal,
            connectionInfo: ChannelConnectionInfo?,
            status: ChannelStatus,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): Channel = Channel(
            id = id,
            propertyId = propertyId,
            code = code,
            name = name,
            type = type,
            commissionRate = commissionRate,
            connectionInfo = connectionInfo,
            status = status,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
