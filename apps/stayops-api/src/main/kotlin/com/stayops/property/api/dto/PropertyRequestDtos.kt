package com.stayops.property.api.dto

import com.stayops.shared.time.TimeZonePolicy
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

data class CreatePropertyRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val type: String,
    @field:Valid val address: AddressRequest,
    @field:Valid val contactInfo: ContactInfoRequest,
    @field:NotBlank val description: String,
    val timezone: String = TimeZonePolicy.DEFAULT_ZONE_ID,
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

data class UpdatePropertyRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val description: String,
    @field:Valid val address: AddressRequest,
    @field:Valid val contactInfo: ContactInfoRequest
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
