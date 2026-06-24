package com.stayops.property.api

import com.stayops.member.domain.model.Member
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.api.security.MemberSessionAuthenticationUpdater
import com.stayops.property.api.dto.CreatePropertyRequest
import com.stayops.property.api.dto.PropertyResponse
import com.stayops.property.api.dto.UpdatePropertyRequest
import com.stayops.property.application.dto.CreatePropertyCommand
import com.stayops.property.application.dto.UpdatePropertyCommand
import com.stayops.property.application.service.PropertyApplication
import com.stayops.property.application.service.PropertyOnboardingApplication
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/properties")
class PropertyApi(
    private val propertyApplication: PropertyApplication,
    private val propertyOnboardingApplication: PropertyOnboardingApplication,
    private val memberAccessApplication: MemberAccessApplication,
    private val memberSessionAuthenticationUpdater: MemberSessionAuthenticationUpdater
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody @Valid request: CreatePropertyRequest,
        @AuthenticationPrincipal currentMember: Member?,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): PropertyResponse {
        val member = memberAccessApplication.requireAuthenticatedMember(currentMember)
        val result = propertyOnboardingApplication.onboardProperty(
            CreatePropertyCommand(
                ownerId = member.id,
                name = request.name,
                type = request.type,
                street = request.address.street,
                city = request.address.city,
                state = request.address.state,
                zipCode = request.address.zipCode,
                country = request.address.country,
                latitude = request.address.latitude,
                longitude = request.address.longitude,
                phone = request.contactInfo.phone,
                email = request.contactInfo.email,
                website = request.contactInfo.website,
                description = request.description,
                timezone = request.timezone,
                currency = request.currency
            )
        )

        memberSessionAuthenticationUpdater.update(result.owner, httpRequest, httpResponse)

        return PropertyResponse.from(result.property)
    }

    @GetMapping
    fun getAll(@AuthenticationPrincipal member: Member?): List<PropertyResponse> {
        val accessibleIds = memberAccessApplication.resolveAccessiblePropertyIds(member)
        return propertyApplication.getAccessiblePropertyViews(accessibleIds)
            .map { PropertyResponse.from(it) }
    }

    @PatchMapping("/{pid}/activate")
    fun activate(
        @PathVariable pid: String,
        @AuthenticationPrincipal member: Member?
    ): PropertyResponse {
        memberAccessApplication.requirePropertyAccess(member, pid)
        return PropertyResponse.from(propertyApplication.activatePropertyView(pid))
    }

    @PatchMapping("/{pid}/deactivate")
    fun deactivate(
        @PathVariable pid: String,
        @AuthenticationPrincipal member: Member?
    ): PropertyResponse {
        memberAccessApplication.requirePropertyAccess(member, pid)
        return PropertyResponse.from(propertyApplication.deactivatePropertyView(pid))
    }

    @GetMapping("/{pid}")
    fun getOne(
        @PathVariable pid: String,
        @AuthenticationPrincipal member: Member?
    ): PropertyResponse {
        memberAccessApplication.requirePropertyAccess(member, pid)
        return PropertyResponse.from(propertyApplication.getPropertyView(pid))
    }

    @PutMapping("/{pid}")
    fun update(
        @PathVariable pid: String,
        @RequestBody @Valid request: UpdatePropertyRequest,
        @AuthenticationPrincipal member: Member?
    ): PropertyResponse {
        memberAccessApplication.requirePropertyAccess(member, pid)
        val property = propertyApplication.updateProperty(
            UpdatePropertyCommand(
                id = pid,
                name = request.name,
                description = request.description,
                street = request.address.street,
                city = request.address.city,
                state = request.address.state,
                zipCode = request.address.zipCode,
                country = request.address.country,
                latitude = request.address.latitude,
                longitude = request.address.longitude,
                phone = request.contactInfo.phone,
                email = request.contactInfo.email,
                website = request.contactInfo.website
            )
        )
        return PropertyResponse.from(property)
    }
}
