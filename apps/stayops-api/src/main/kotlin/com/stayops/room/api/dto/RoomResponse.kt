package com.stayops.room.api.dto

import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomStatus
import java.time.Instant

data class RoomResponse(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val roomNumber: String,
    val floor: Int,
    val status: RoomStatus,
    val memo: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(room: Room): RoomResponse = RoomResponse(
            id = room.id,
            propertyId = room.propertyId,
            roomTypeId = room.roomTypeId,
            roomNumber = room.roomNumber,
            floor = room.floor,
            status = room.status,
            memo = room.memo,
            createdAt = room.createdAt,
            updatedAt = room.updatedAt
        )
    }
}
