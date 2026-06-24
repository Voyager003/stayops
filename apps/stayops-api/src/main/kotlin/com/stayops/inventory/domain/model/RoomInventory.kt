package com.stayops.inventory.domain.model

import java.time.Instant
import java.time.LocalDate

class RoomInventory private constructor(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val date: LocalDate,
    val totalCount: Int,
    val reservedCount: Int,
    val blockedCount: Int,
    val version: Long?,
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
        return copyState(reservedCount = reservedCount + 1)
    }

    fun release(): RoomInventory {
        require(reservedCount > 0) {
            "취소할 예약이 없습니다: reservedCount=$reservedCount"
        }
        return copyState(reservedCount = reservedCount - 1)
    }

    fun block(count: Int): RoomInventory {
        require(count >= 1) { "차단 수는 1 이상이어야 합니다: $count" }
        val actual = minOf(count, availableCount)
        if (actual == 0) return this
        return copyState(blockedCount = blockedCount + actual)
    }

    fun updateTotalCount(newTotalCount: Int): RoomInventory {
        require(newTotalCount >= 1) { "총 객실 수는 1 이상이어야 합니다: $newTotalCount" }
        require(reservedCount + blockedCount <= newTotalCount) {
            "총 객실 수가 현재 예약+차단 수보다 작을 수 없습니다: reserved=$reservedCount, blocked=$blockedCount, newTotal=$newTotalCount"
        }
        return copyState(totalCount = newTotalCount)
    }

    fun unblock(count: Int): RoomInventory {
        require(count >= 1) { "해제 수는 1 이상이어야 합니다: $count" }
        val actual = minOf(count, blockedCount)
        if (actual == 0) return this
        return copyState(blockedCount = blockedCount - actual)
    }

    private fun copyState(
        totalCount: Int = this.totalCount,
        reservedCount: Int = this.reservedCount,
        blockedCount: Int = this.blockedCount,
        updatedAt: Instant = Instant.now()
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RoomInventory

        return id == other.id
    }

    override fun hashCode(): Int = 31 * javaClass.hashCode() + id.hashCode()

    override fun toString(): String =
        "RoomInventory(id=$id, propertyId=$propertyId, roomTypeId=$roomTypeId, date=$date)"

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
                blockedCount = totalCount,
                version = null,
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
            version: Long?,
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
