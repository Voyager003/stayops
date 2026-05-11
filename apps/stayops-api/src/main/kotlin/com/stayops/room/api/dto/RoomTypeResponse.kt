package com.stayops.room.api.dto

import com.stayops.room.domain.model.RoomType
import java.math.BigDecimal
import java.time.Instant

data class RoomTypeResponse(
    val id: String,
    val propertyId: String,
    val name: String,
    val description: String,
    val maxOccupancy: Int,
    val basePrice: MoneyResponse,
    val amenities: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    data class MoneyResponse(
        val amount: BigDecimal,
        val currency: String
    )

    companion object {
        fun from(roomType: RoomType): RoomTypeResponse = RoomTypeResponse(
            id = roomType.id,
            propertyId = roomType.propertyId,
            name = roomType.name,
            description = roomType.description,
            maxOccupancy = roomType.maxOccupancy,
            basePrice = MoneyResponse(roomType.basePrice.amount, roomType.basePrice.currency),
            amenities = roomType.amenities,
            createdAt = roomType.createdAt,
            updatedAt = roomType.updatedAt
        )
    }
}
