package com.stayops.room.api.dto

import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomStatus
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class CreateRoomRequest(
    @field:NotBlank val roomTypeId: String,
    @field:NotBlank val roomNumber: String,
    @field:Min(1) val floor: Int
)

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

data class UpdateRoomStatusRequest(
    @field:NotNull val action: RoomStatusAction
)

enum class RoomStatusAction {
    CHECK_IN, CHECK_OUT, COMPLETE_CLEANING, START_MAINTENANCE, COMPLETE_MAINTENANCE
}
