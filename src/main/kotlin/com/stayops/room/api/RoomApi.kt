package com.stayops.room.api

import com.stayops.room.api.dto.CreateRoomRequest
import com.stayops.room.api.dto.RoomResponse
import com.stayops.room.api.dto.UpdateRoomStatusRequest
import com.stayops.room.application.service.RoomApplication
import com.stayops.shared.security.PropertyAccessChecker
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/properties/{pid}/rooms")
class RoomApi(
    private val roomApplication: RoomApplication,
    private val propertyAccessChecker: PropertyAccessChecker
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable pid: String,
        @RequestBody @Valid request: CreateRoomRequest
    ): RoomResponse {
        propertyAccessChecker.requireAccess(pid)
        val room = roomApplication.createRoom(
            propertyId = pid,
            roomTypeId = request.roomTypeId,
            roomNumber = request.roomNumber,
            floor = request.floor
        )
        return RoomResponse.from(room)
    }

    @GetMapping
    fun getAll(@PathVariable pid: String): List<RoomResponse> {
        propertyAccessChecker.requireAccess(pid)
        return roomApplication.getRoomsByProperty(pid).map { RoomResponse.from(it) }
    }

    @GetMapping("/{id}")
    fun getOne(
        @PathVariable pid: String,
        @PathVariable id: String
    ): RoomResponse {
        propertyAccessChecker.requireAccess(pid)
        return RoomResponse.from(roomApplication.getRoom(id))
    }

    @PutMapping("/{id}")
    fun updateMemo(
        @PathVariable pid: String,
        @PathVariable id: String,
        @RequestParam memo: String?
    ): RoomResponse {
        propertyAccessChecker.requireAccess(pid)
        return RoomResponse.from(roomApplication.updateMemo(id, memo))
    }

    @PatchMapping("/{id}/status")
    fun changeStatus(
        @PathVariable pid: String,
        @PathVariable id: String,
        @RequestBody @Valid request: UpdateRoomStatusRequest
    ): RoomResponse {
        propertyAccessChecker.requireAccess(pid)
        return RoomResponse.from(roomApplication.changeStatus(id, request.action))
    }
}
