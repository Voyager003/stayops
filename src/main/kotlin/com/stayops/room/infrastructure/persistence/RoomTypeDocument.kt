package com.stayops.room.infrastructure.persistence

import com.stayops.room.domain.model.RoomType
import com.stayops.shared.domain.Money
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

@Document("room_types")
@CompoundIndex(def = "{'propertyId': 1, 'name': 1}", unique = true)
data class RoomTypeDocument(
    @Id val id: String,
    val propertyId: String,
    val name: String,
    val description: String,
    val maxOccupancy: Int,
    val basePrice: MoneyData,
    val amenities: List<String>,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    data class MoneyData(
        val amount: BigDecimal,
        val currency: String
    )

    fun toDomain(): RoomType = RoomType.reconstitute(
        id = id,
        propertyId = propertyId,
        name = name,
        description = description,
        maxOccupancy = maxOccupancy,
        basePrice = Money.of(basePrice.amount, basePrice.currency),
        amenities = amenities,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(roomType: RoomType): RoomTypeDocument = RoomTypeDocument(
            id = roomType.id,
            propertyId = roomType.propertyId,
            name = roomType.name,
            description = roomType.description,
            maxOccupancy = roomType.maxOccupancy,
            basePrice = MoneyData(roomType.basePrice.amount, roomType.basePrice.currency),
            amenities = roomType.amenities,
            version = roomType.version,
            createdAt = roomType.createdAt,
            updatedAt = roomType.updatedAt
        )
    }
}
