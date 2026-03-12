package com.stayops.property.api

import com.stayops.property.api.dto.CreatePropertyRequest
import com.stayops.property.api.dto.PropertyResponse
import com.stayops.property.api.dto.UpdatePropertyRequest
import com.stayops.property.application.service.PropertyApplication
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
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
    private val propertyApplication: PropertyApplication
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody @Valid request: CreatePropertyRequest): PropertyResponse {
        val property = propertyApplication.createProperty(
            ownerId = request.ownerId,
            name = request.name,
            type = request.type,
            address = Address.of(request.address.street, request.address.city, request.address.state, request.address.zipCode, request.address.country),
            contactInfo = ContactInfo.of(request.contactInfo.phone, request.contactInfo.email, request.contactInfo.website),
            description = request.description,
            timezone = request.timezone,
            currency = request.currency
        )
        return PropertyResponse.from(property)
    }

    @GetMapping
    fun getAll(): List<PropertyResponse> =
        propertyApplication.getAllProperties().map { PropertyResponse.from(it) }

    @GetMapping("/{pid}")
    fun getOne(@PathVariable pid: String): PropertyResponse =
        PropertyResponse.from(propertyApplication.getProperty(pid))

    @PutMapping("/{pid}")
    fun update(
        @PathVariable pid: String,
        @RequestBody @Valid request: UpdatePropertyRequest
    ): PropertyResponse {
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
