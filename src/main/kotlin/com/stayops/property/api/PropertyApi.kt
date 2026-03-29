package com.stayops.property.api

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.repository.MemberRepository
import com.stayops.property.api.dto.CreatePropertyRequest
import com.stayops.property.api.dto.PropertyResponse
import com.stayops.property.api.dto.UpdatePropertyRequest
import com.stayops.property.application.service.PropertyApplication
import com.stayops.shared.security.PropertyAccessChecker
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/api/v1/properties")
class PropertyApi(
    private val propertyApplication: PropertyApplication,
    private val propertyAccessChecker: PropertyAccessChecker,
    private val memberRepository: MemberRepository
) {
    private val securityContextRepository = HttpSessionSecurityContextRepository()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody @Valid request: CreatePropertyRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): PropertyResponse {
        val currentMember = SecurityContextHolder.getContext().authentication?.principal as Member
        val property = propertyApplication.createProperty(
            ownerId = currentMember.id,
            name = request.name,
            type = request.type,
            address = Address.of(request.address.street, request.address.city, request.address.state, request.address.zipCode, request.address.country),
            contactInfo = ContactInfo.of(request.contactInfo.phone, request.contactInfo.email, request.contactInfo.website),
            description = request.description,
            timezone = request.timezone,
            currency = request.currency
        )

        // SecurityContext의 Member를 갱신하여 세션에 즉시 반영
        val updatedMember = memberRepository.findById(currentMember.id)
        if (updatedMember != null) {
            val newAuth = UsernamePasswordAuthenticationToken(updatedMember, null, emptyList())
            val context = SecurityContextHolder.getContext()
            context.authentication = newAuth
            securityContextRepository.saveContext(context, httpRequest, httpResponse)
        }

        return PropertyResponse.from(property)
    }

    @GetMapping
    fun getAll(): List<PropertyResponse> =
        propertyApplication.getAllProperties().map { PropertyResponse.from(it) }

    @PatchMapping("/{pid}/activate")
    fun activate(@PathVariable pid: String): PropertyResponse {
        propertyAccessChecker.requireAccess(pid)
        return PropertyResponse.from(propertyApplication.activateProperty(pid))
    }

    @PatchMapping("/{pid}/deactivate")
    fun deactivate(@PathVariable pid: String): PropertyResponse {
        propertyAccessChecker.requireAccess(pid)
        return PropertyResponse.from(propertyApplication.deactivateProperty(pid))
    }

    @GetMapping("/{pid}")
    fun getOne(@PathVariable pid: String): PropertyResponse {
        propertyAccessChecker.requireAccess(pid)
        return PropertyResponse.from(propertyApplication.getProperty(pid))
    }

    @PutMapping("/{pid}")
    fun update(
        @PathVariable pid: String,
        @RequestBody @Valid request: UpdatePropertyRequest
    ): PropertyResponse {
        propertyAccessChecker.requireAccess(pid)
        val property = propertyApplication.updateProperty(
            id = pid,
            name = request.name,
            description = request.description,
            address = Address.of(request.address.street, request.address.city, request.address.state, request.address.zipCode, request.address.country),
            contactInfo = ContactInfo.of(request.contactInfo.phone, request.contactInfo.email, request.contactInfo.website)
        )
        return PropertyResponse.from(property)
    }
}
