package com.stayops.guest.api

import com.stayops.guest.api.dto.GuestResponse
import com.stayops.guest.api.dto.UpdateGuestRequest
import com.stayops.guest.application.service.GuestApplication
import com.stayops.shared.security.PropertyAccessChecker
import com.stayops.guest.domain.model.GuestTier
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/properties/{pid}")
class GuestApi(
    private val guestApplication: GuestApplication,
    private val propertyAccessChecker: PropertyAccessChecker
) {
    @GetMapping("/guests")
    fun getGuests(
        @PathVariable pid: String,
        @RequestParam(required = false) tier: GuestTier?,
        @RequestParam(required = false) name: String?
    ): List<GuestResponse> {
        propertyAccessChecker.requireAccess(pid)
        return guestApplication.getGuests(pid, tier, name).map { GuestResponse.from(it) }
    }

    @GetMapping("/guests/{guestId}")
    fun getGuest(
        @PathVariable pid: String,
        @PathVariable guestId: String
    ): GuestResponse {
        propertyAccessChecker.requireAccess(pid)
        return GuestResponse.from(guestApplication.getGuest(guestId))
    }

    @PutMapping("/guests/{guestId}")
    fun updateGuest(
        @PathVariable pid: String,
        @PathVariable guestId: String,
        @RequestBody @Valid request: UpdateGuestRequest
    ): GuestResponse {
        propertyAccessChecker.requireAccess(pid)
        return GuestResponse.from(guestApplication.updateGuest(guestId, request.name, request.memo))
    }
}
