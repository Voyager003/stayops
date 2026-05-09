package com.stayops.channel.api.dto

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.model.ChannelType
import java.math.BigDecimal
import java.time.Instant

data class ChannelResponse(
    val id: String,
    val propertyId: String,
    val code: String,
    val name: String,
    val type: ChannelType,
    val commissionRate: BigDecimal,
    val status: ChannelStatus,
    val apiEndpoint: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(channel: Channel) = ChannelResponse(
            id = channel.id,
            propertyId = channel.propertyId,
            code = channel.code,
            name = channel.name,
            type = channel.type,
            commissionRate = channel.commissionRate,
            status = channel.status,
            apiEndpoint = channel.connectionInfo?.apiEndpoint,
            createdAt = channel.createdAt,
            updatedAt = channel.updatedAt
        )
    }
}
