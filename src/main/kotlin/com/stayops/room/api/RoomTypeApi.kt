package com.stayops.room.api

import com.stayops.room.api.dto.CreateRoomTypeRequest
import com.stayops.room.api.dto.RoomTypeResponse
import com.stayops.room.api.dto.UpdateRoomTypeRequest
import com.stayops.room.application.service.RoomTypeApplication
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/properties/{pid}/room-types")
class RoomTypeApi(
    private val roomTypeApplication: RoomTypeApplication
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable pid: String,
        @RequestBody @Valid request: CreateRoomTypeRequest
    ): RoomTypeResponse {
        val roomType = roomTypeApplication.createRoomType(
            propertyId = pid,
            name = request.name,
            description = request.description,
            maxOccupancy = request.maxOccupancy,
            basePrice = request.basePrice,
            currency = request.currency,
            amenities = request.amenities
        )
        return RoomTypeResponse.from(roomType)
    }

    @GetMapping
    fun getAll(@PathVariable pid: String): List<RoomTypeResponse> =
        roomTypeApplication.getRoomTypesByProperty(pid).map { RoomTypeResponse.from(it) }

    @GetMapping("/{id}")
    fun getOne(
        @PathVariable pid: String,
        @PathVariable id: String
    ): RoomTypeResponse = RoomTypeResponse.from(roomTypeApplication.getRoomType(id))

    @PutMapping("/{id}")
    fun update(
        @PathVariable pid: String,
        @PathVariable id: String,
        @RequestBody @Valid request: UpdateRoomTypeRequest
    ): RoomTypeResponse {
        val roomType = roomTypeApplication.updateRoomType(
            id = id,
            name = request.name,
            description = request.description,
            maxOccupancy = request.maxOccupancy,
            basePrice = request.basePrice,
            currency = request.currency,
            amenities = request.amenities
        )
        return RoomTypeResponse.from(roomType)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivate(
        @PathVariable pid: String,
        @PathVariable id: String
    ) = roomTypeApplication.deactivateRoomType(id)
}
