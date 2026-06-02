package com.stayops.property.api.dto

import com.stayops.property.domain.model.PropertyType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class CreatePropertyRequest(
    @field:NotBlank val name: String,
    @field:NotNull val type: PropertyType,
    @field:Valid val address: AddressRequest,
    @field:Valid val contactInfo: ContactInfoRequest,
    @field:NotBlank val description: String,
    val timezone: String = "Asia/Seoul",
    val currency: String = "KRW"
) {
    data class AddressRequest(
        @field:NotBlank val street: String,
        @field:NotBlank val city: String,
        @field:NotBlank val state: String,
        @field:NotBlank val zipCode: String,
        @field:NotBlank val country: String,
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    data class ContactInfoRequest(
        @field:NotBlank val phone: String,
        @field:NotBlank val email: String,
        @field:Pattern(regexp = "^https?://.+", message = "웹사이트는 http 또는 https URL이어야 합니다")
        val website: String? = null
    )
}
