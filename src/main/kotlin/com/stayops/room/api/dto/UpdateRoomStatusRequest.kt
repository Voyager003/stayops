package com.stayops.room.api.dto

import jakarta.validation.constraints.NotNull

data class UpdateRoomStatusRequest(
    @field:NotNull val action: RoomStatusAction
)

enum class RoomStatusAction {
    CHECK_IN, CHECK_OUT, COMPLETE_CLEANING, START_MAINTENANCE, COMPLETE_MAINTENANCE
}
