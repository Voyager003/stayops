package com.stayops.channel.infrastructure.persistence

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelPolicy
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.model.ChannelType
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

@Document("channels")
data class ChannelDocument(
    @Id val id: String,
    val propertyId: String,
    val code: String,
    val name: String,
    val type: ChannelType,
    val commissionRate: BigDecimal,
    val webhookSecret: String?,
    val status: ChannelStatus,
    @Version val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toDomain(): Channel {
        val policy = when (type) {
            ChannelType.DIRECT -> ChannelPolicy.DirectPolicy(commissionRate)
            ChannelType.OTA -> ChannelPolicy.OtaPolicy.reconstitute(commissionRate, webhookSecret!!)
        }
        return Channel.reconstitute(
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

    companion object {
        fun from(channel: Channel): ChannelDocument {
            val (commissionRate, webhookSecret) = when (val policy = channel.policy) {
                is ChannelPolicy.DirectPolicy -> policy.commissionRate to null
                is ChannelPolicy.OtaPolicy -> policy.commissionRate to policy.webhookSecret
            }
            return ChannelDocument(
                id = channel.id,
                propertyId = channel.propertyId,
                code = channel.code,
                name = channel.name,
                type = channel.type,
                commissionRate = commissionRate,
                webhookSecret = webhookSecret,
                status = channel.status,
                version = channel.version,
                createdAt = channel.createdAt,
                updatedAt = channel.updatedAt
            )
        }
    }
}
