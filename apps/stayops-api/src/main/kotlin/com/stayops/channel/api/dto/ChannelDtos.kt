package com.stayops.channel.api.dto

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.model.ChannelType
import com.stayops.channel.application.required.MockOtaRandomBookingResult
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
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

data class CreateChannelRequest(
    @field:NotBlank
    val code: String,
    @field:NotBlank
    val name: String,
    @field:DecimalMin("0.01")
    val commissionRate: BigDecimal
)

data class RandomBookingSimulationResponse(
    val status: String,
    val bookingId: String,
    val roomTypeId: String,
    val date: String,
    val guestName: String
) {
    companion object {
        fun from(result: MockOtaRandomBookingResult) = RandomBookingSimulationResponse(
            status = result.status,
            bookingId = result.bookingId,
            roomTypeId = result.roomTypeId,
            date = result.date,
            guestName = result.guestName
        )
    }
}

data class UpdateChannelRequest(
    val name: String? = null,
    @field:DecimalMin("0.01")
    val commissionRate: BigDecimal? = null
)
