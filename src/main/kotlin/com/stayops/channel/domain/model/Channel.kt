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
    val policy: ChannelPolicy,
    val status: ChannelStatus,
    val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun activate(): Channel = copy(status = ChannelStatus.ACTIVE, updatedAt = Instant.now())

    fun deactivate(): Channel = copy(status = ChannelStatus.INACTIVE, updatedAt = Instant.now())

    fun suspend(): Channel = copy(status = ChannelStatus.SUSPENDED, updatedAt = Instant.now())

    companion object {
        private const val FINESTAY_CODE = "FINESTAY"
        private const val FINESTAY_NAME = "FineStay"

        fun createDirect(id: String, propertyId: String): Channel {
            val now = Instant.now()
            return Channel(
                id = id,
                propertyId = propertyId,
                code = FINESTAY_CODE,
                name = FINESTAY_NAME,
                type = ChannelType.DIRECT,
                policy = ChannelPolicy.DirectPolicy(),
                status = ChannelStatus.ACTIVE,
                version = null,
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
            webhookSecret: String
        ): Channel {
            require(code.isNotBlank()) { "채널 코드는 공백일 수 없습니다." }
            require(name.isNotBlank()) { "채널 이름은 공백일 수 없습니다." }

            val now = Instant.now()
            return Channel(
                id = id,
                propertyId = propertyId,
                code = code,
                name = name,
                type = ChannelType.OTA,
                policy = ChannelPolicy.OtaPolicy.of(commissionRate, webhookSecret),
                status = ChannelStatus.ACTIVE,
                version = null,
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
            policy: ChannelPolicy,
            status: ChannelStatus,
            version: Long?,
            createdAt: Instant,
            updatedAt: Instant
        ): Channel = Channel(
            id = id,
            propertyId = propertyId,
            code = code,
            name = name,
            type = type,
            policy = policy,
            status = status,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
