package com.stayops.inventory.api

import com.stayops.inventory.api.dto.AvailabilityResponse
import com.stayops.inventory.api.dto.InventoryUpdateAction
import com.stayops.inventory.api.dto.OpenInventoryRequest
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
    @PostMapping("/inventory/open")
    @ResponseStatus(HttpStatus.CREATED)
    fun openInventory(
        @PathVariable pid: String,
        @RequestBody @Valid request: OpenInventoryRequest
    ): Map<String, Int> {
        propertyAccessChecker.requireAccess(pid)
        val created = inventoryApplication.openInventory(
            propertyId = pid,
            roomTypeId = request.roomTypeId,
            startDate = request.startDate,
            endDate = request.endDate
        )
        return mapOf("createdDays" to created)
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
