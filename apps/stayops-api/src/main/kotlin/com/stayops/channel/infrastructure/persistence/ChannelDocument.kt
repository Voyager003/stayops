package com.stayops.channel.infrastructure.persistence

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelConnectionInfo
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
    val connectionInfo: ConnectionInfoData?,
    val status: ChannelStatus,
    @Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    data class ConnectionInfoData(
        val apiEndpoint: String
    )

    fun toDomain(): Channel = Channel.reconstitute(
        id = id,
        propertyId = propertyId,
        code = code,
        name = name,
        type = type,
        commissionRate = commissionRate,
        connectionInfo = connectionInfo?.let {
            ChannelConnectionInfo(
                apiEndpoint = it.apiEndpoint
            )
        },
        status = status,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(channel: Channel): ChannelDocument = ChannelDocument(
            id = channel.id,
            propertyId = channel.propertyId,
            code = channel.code,
            name = channel.name,
            type = channel.type,
            commissionRate = channel.commissionRate,
            connectionInfo = channel.connectionInfo?.let {
                ConnectionInfoData(
                    apiEndpoint = it.apiEndpoint
                )
            },
            status = channel.status,
            version = channel.version,
            createdAt = channel.createdAt,
            updatedAt = channel.updatedAt
        )
    }
}
