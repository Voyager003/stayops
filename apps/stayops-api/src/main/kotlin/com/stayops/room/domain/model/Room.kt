package com.stayops.room.domain.model

import java.time.Instant

class Room private constructor(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val roomNumber: String,
    val floor: Int,
    val status: RoomStatus,
    val memo: String?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(roomNumber.isNotBlank()) { "객실 번호는 비어있을 수 없습니다" }
        require(floor >= 1) { "층은 1 이상이어야 합니다: $floor" }
    }

    fun checkIn(): Room {
        require(status == RoomStatus.AVAILABLE) {
            "AVAILABLE 상태에서만 체크인할 수 있습니다: $status"
        }
        return copyState(status = RoomStatus.OCCUPIED, updatedAt = Instant.now())
    }

    fun checkOut(): Room {
        require(status == RoomStatus.OCCUPIED) {
            "OCCUPIED 상태에서만 체크아웃할 수 있습니다: $status"
        }
        return copyState(status = RoomStatus.CLEANING, updatedAt = Instant.now())
    }

    fun completeCleaning(): Room {
        require(status == RoomStatus.CLEANING) {
            "CLEANING 상태에서만 청소 완료할 수 있습니다: $status"
        }
        return copyState(status = RoomStatus.AVAILABLE, updatedAt = Instant.now())
    }

    fun startMaintenance(): Room {
        require(status == RoomStatus.AVAILABLE) {
            "AVAILABLE 상태에서만 정비를 시작할 수 있습니다: $status"
        }
        return copyState(status = RoomStatus.MAINTENANCE, updatedAt = Instant.now())
    }

    fun completeMaintenance(): Room {
        require(status == RoomStatus.MAINTENANCE) {
            "MAINTENANCE 상태에서만 정비 완료할 수 있습니다: $status"
        }
        return copyState(status = RoomStatus.AVAILABLE, updatedAt = Instant.now())
    }

    fun updateMemo(memo: String?): Room {
        return copyState(memo = memo, updatedAt = Instant.now())
    }

    private fun copyState(
        status: RoomStatus = this.status,
        memo: String? = this.memo,
        updatedAt: Instant = this.updatedAt
    ): Room = Room(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        roomNumber = roomNumber,
        floor = floor,
        status = status,
        memo = memo,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun equals(other: Any?): Boolean =
        this === other || (other is Room && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Room(id=$id, propertyId=$propertyId, roomTypeId=$roomTypeId, roomNumber=$roomNumber, status=$status)"

    companion object {
        fun create(
            id: String,
            propertyId: String,
            roomTypeId: String,
            roomNumber: String,
            floor: Int
        ): Room {
            val now = Instant.now()
            return Room(
                id = id,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                roomNumber = roomNumber,
                floor = floor,
                status = RoomStatus.AVAILABLE,
                memo = null,
                version = 0,
                createdAt = now,
                updatedAt = now
            )
        }

        // Reconstitutes a Room from persistence — bypasses business creation logic
        fun reconstitute(
            id: String,
            propertyId: String,
            roomTypeId: String,
            roomNumber: String,
            floor: Int,
            status: RoomStatus,
            memo: String?,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): Room = Room(
            id = id,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            roomNumber = roomNumber,
            floor = floor,
            status = status,
            memo = memo,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
