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
    val heldCount: Int,
    val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(totalCount >= 1) { "총 객실 수는 1 이상이어야 합니다: $totalCount" }
        require(reservedCount >= 0) { "예약 수는 0 이상이어야 합니다: $reservedCount" }
        require(blockedCount >= 0) { "차단 수는 0 이상이어야 합니다: $blockedCount" }
        require(heldCount >= 0) { "점유 수는 0 이상이어야 합니다: $heldCount" }
        require(reservedCount + blockedCount + heldCount <= totalCount) {
            "예약 수 + 차단 수 + 점유 수가 총 객실 수를 초과할 수 없습니다: reserved=$reservedCount, blocked=$blockedCount, held=$heldCount, total=$totalCount"
        }
    }

    val availableCount: Int
        get() = totalCount - reservedCount - blockedCount - heldCount

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

    fun hold(): RoomInventory {
        require(availableCount >= 1) {
            "가용 객실이 없습니다: available=$availableCount"
        }
        return copyState(heldCount = heldCount + 1)
    }

    fun releaseHold(): RoomInventory {
        require(heldCount > 0) {
            "해제할 점유가 없습니다: heldCount=$heldCount"
        }
        return copyState(heldCount = heldCount - 1)
    }

    fun consumeHold(): RoomInventory {
        require(heldCount > 0) {
            "소비할 점유가 없습니다: heldCount=$heldCount"
        }
        return copyState(
            reservedCount = reservedCount + 1,
            heldCount = heldCount - 1
        )
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
        heldCount: Int = this.heldCount,
        updatedAt: Instant = Instant.now()
    ): RoomInventory = RoomInventory(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        date = date,
        totalCount = totalCount,
        reservedCount = reservedCount,
        blockedCount = blockedCount,
        heldCount = heldCount,
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
                heldCount = 0,
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
            heldCount: Int = 0,
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
            heldCount = heldCount,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
