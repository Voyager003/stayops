package com.stayops.inventory.api

import com.stayops.inventory.api.dto.AvailabilityResponse
import com.stayops.inventory.api.dto.InitializeInventoryRequest
import com.stayops.inventory.api.dto.InventoryUpdateAction
import com.stayops.inventory.api.dto.UpdateInventoryRequest
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.shared.security.PropertyAccessChecker
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/properties/{pid}")
class RoomInventoryApi(
    private val inventoryApplication: RoomInventoryApplication,
    private val propertyAccessChecker: PropertyAccessChecker
) {
    @PostMapping("/inventory/initialize")
    @ResponseStatus(HttpStatus.CREATED)
    fun initialize(
        @PathVariable pid: String,
        @RequestBody @Valid request: InitializeInventoryRequest
    ): AvailabilityResponse {
        propertyAccessChecker.requireAccess(pid)
        val inventory = inventoryApplication.initializeInventory(
            propertyId = pid,
            roomTypeId = request.roomTypeId,
            date = request.date,
            totalCount = request.totalCount
        )
        return AvailabilityResponse.from(inventory)
    }

    @GetMapping("/availability")
    fun getAvailability(
        @PathVariable pid: String,
        @RequestParam roomTypeId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate
    ): List<AvailabilityResponse> {
        propertyAccessChecker.requireAccess(pid)
        return inventoryApplication.getAvailability(pid, roomTypeId, startDate, endDate)
            .map { AvailabilityResponse.from(it) }
    }

    @PutMapping("/inventory/{roomTypeId}/{date}")
    fun updateInventory(
        @PathVariable pid: String,
        @PathVariable roomTypeId: String,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestBody @Valid request: UpdateInventoryRequest
    ): AvailabilityResponse {
        propertyAccessChecker.requireAccess(pid)
        val inventory = when (request.action) {
            InventoryUpdateAction.BLOCK -> inventoryApplication.blockInventory(pid, roomTypeId, date, request.count)
            InventoryUpdateAction.UNBLOCK -> inventoryApplication.unblockInventory(pid, roomTypeId, date, request.count)
        }
        return AvailabilityResponse.from(inventory)
    }
}
