package com.stayops.inventory.infrastructure.cache

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.application.required.RoomInventoryCache
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Service
class RedisRoomInventoryCache(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : RoomInventoryCache {
    // Stored as flat JSON to avoid private-constructor deserialization issues
    private data class CacheEntry(
        val id: String,
        val propertyId: String,
        val roomTypeId: String,
        val date: String,
        val totalCount: Int,
        val reservedCount: Int,
        val blockedCount: Int,
        val version: Long?,
        val createdAt: Long,
        val updatedAt: Long
    ) {
        fun toDomain(): RoomInventory = RoomInventory.reconstitute(
            id = id,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            date = LocalDate.parse(date),
            totalCount = totalCount,
            reservedCount = reservedCount,
            blockedCount = blockedCount,
            version = version,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt)
        )

        companion object {
            fun from(inventory: RoomInventory) = CacheEntry(
                id = inventory.id,
                propertyId = inventory.propertyId,
                roomTypeId = inventory.roomTypeId,
                date = inventory.date.toString(),
                totalCount = inventory.totalCount,
                reservedCount = inventory.reservedCount,
                blockedCount = inventory.blockedCount,
                version = inventory.version,
                createdAt = inventory.createdAt.toEpochMilli(),
                updatedAt = inventory.updatedAt.toEpochMilli()
            )
        }
    }

    private fun key(propertyId: String, roomTypeId: String, date: LocalDate) =
        "inventory:$propertyId:$roomTypeId:$date"

    override fun get(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory? {
        val json = redisTemplate.opsForValue().get(key(propertyId, roomTypeId, date)) ?: return null
        return objectMapper.readValue(json, CacheEntry::class.java).toDomain()
    }

    override fun put(inventory: RoomInventory) {
        val json = objectMapper.writeValueAsString(CacheEntry.from(inventory))
        redisTemplate.opsForValue().set(
            key(inventory.propertyId, inventory.roomTypeId, inventory.date),
            json,
            5, TimeUnit.MINUTES
        )
    }

    override fun evict(propertyId: String, roomTypeId: String, date: LocalDate) {
        redisTemplate.delete(key(propertyId, roomTypeId, date))
    }
}
