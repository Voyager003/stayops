package com.stayops.inventory.domain.model

import java.time.Instant
import java.time.LocalDate

@ConsistentCopyVisibility
data class RoomInventory private constructor(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val date: LocalDate,
    val totalCount: Int,
    val reservedCount: Int,
    val blockedCount: Int,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(totalCount >= 1) { "총 객실 수는 1 이상이어야 합니다: $totalCount" }
        require(reservedCount >= 0) { "예약 수는 0 이상이어야 합니다: $reservedCount" }
        require(blockedCount >= 0) { "차단 수는 0 이상이어야 합니다: $blockedCount" }
        require(reservedCount + blockedCount <= totalCount) {
            "예약 수 + 차단 수가 총 객실 수를 초과할 수 없습니다: reserved=$reservedCount, blocked=$blockedCount, total=$totalCount"
        }
    }

    val availableCount: Int
        get() = totalCount - reservedCount - blockedCount

    fun reserve(): RoomInventory {
        require(availableCount >= 1) {
            "가용 객실이 없습니다: available=$availableCount"
        }
        return copy(reservedCount = reservedCount + 1, updatedAt = Instant.now())
    }

    fun release(): RoomInventory {
        require(reservedCount > 0) {
            "취소할 예약이 없습니다: reservedCount=$reservedCount"
        }
        return copy(reservedCount = reservedCount - 1, updatedAt = Instant.now())
    }

    fun block(count: Int): RoomInventory {
        require(count >= 1) { "차단 수는 1 이상이어야 합니다: $count" }
        require(availableCount >= count) {
            "가용 객실이 부족합니다: available=$availableCount, requested=$count"
        }
        return copy(blockedCount = blockedCount + count, updatedAt = Instant.now())
    }

    fun unblock(count: Int): RoomInventory {
        require(count >= 1) { "해제 수는 1 이상이어야 합니다: $count" }
        require(blockedCount >= count) {
            "차단된 객실이 부족합니다: blockedCount=$blockedCount, requested=$count"
        }
        return copy(blockedCount = blockedCount - count, updatedAt = Instant.now())
    }

    companion object {
        fun create(
            id: String,
            propertyId: String,
            roomTypeId: String,
            date: LocalDate,
            totalCount: Int
        ): RoomInventory {
            val now = Instant.now()
            return RoomInventory(
                id = id,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                date = date,
                totalCount = totalCount,
                reservedCount = 0,
                blockedCount = 0,
                version = 0,
                createdAt = now,
                updatedAt = now
            )
        }

        // Reconstitutes a RoomInventory from persistence — bypasses business creation logic
        fun reconstitute(
            id: String,
            propertyId: String,
            roomTypeId: String,
            date: LocalDate,
            totalCount: Int,
            reservedCount: Int,
            blockedCount: Int,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): RoomInventory = RoomInventory(
            id = id,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            date = date,
            totalCount = totalCount,
            reservedCount = reservedCount,
            blockedCount = blockedCount,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
