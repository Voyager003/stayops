package com.stayops.room.domain.model

import com.stayops.shared.domain.Money
import java.math.BigDecimal
import java.time.Instant

@ConsistentCopyVisibility
data class RoomType private constructor(
    val id: String,
    val propertyId: String,
    val name: String,
    val description: String,
    val maxOccupancy: Int,
    val basePrice: Money,
    val amenities: List<String>,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(name.isNotBlank()) { "객실타입 이름은 비어있을 수 없습니다" }
        require(maxOccupancy >= 1) { "최대 수용 인원은 1 이상이어야 합니다: $maxOccupancy" }
        require(basePrice.amount > BigDecimal.ZERO) { "기본 요금은 0보다 커야 합니다: ${basePrice.amount}" }
    }

    fun updateInfo(
        name: String = this.name,
        description: String = this.description,
        maxOccupancy: Int = this.maxOccupancy,
        basePrice: Money = this.basePrice,
        amenities: List<String> = this.amenities
    ): RoomType {
        require(name.isNotBlank()) { "객실타입 이름은 비어있을 수 없습니다" }
        require(maxOccupancy >= 1) { "최대 수용 인원은 1 이상이어야 합니다: $maxOccupancy" }
        require(basePrice.amount > BigDecimal.ZERO) { "기본 요금은 0보다 커야 합니다: ${basePrice.amount}" }
        return copy(
            name = name,
            description = description,
            maxOccupancy = maxOccupancy,
            basePrice = basePrice,
            amenities = amenities,
            updatedAt = Instant.now()
        )
    }

    companion object {
        fun create(
            id: String,
            propertyId: String,
            name: String,
            description: String,
            maxOccupancy: Int,
            basePrice: Money,
            amenities: List<String> = emptyList()
        ): RoomType {
            val now = Instant.now()
            return RoomType(
                id = id,
                propertyId = propertyId,
                name = name,
                description = description,
                maxOccupancy = maxOccupancy,
                basePrice = basePrice,
                amenities = amenities,
                version = 0,
                createdAt = now,
                updatedAt = now
            )
        }

        // Reconstitutes a RoomType from persistence — bypasses business creation logic
        fun reconstitute(
            id: String,
            propertyId: String,
            name: String,
            description: String,
            maxOccupancy: Int,
            basePrice: Money,
            amenities: List<String>,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): RoomType = RoomType(
            id = id,
            propertyId = propertyId,
            name = name,
            description = description,
            maxOccupancy = maxOccupancy,
            basePrice = basePrice,
            amenities = amenities,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
