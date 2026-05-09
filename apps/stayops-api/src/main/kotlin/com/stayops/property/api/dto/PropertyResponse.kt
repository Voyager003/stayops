package com.stayops.property.api.dto

import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyStatus
import com.stayops.property.domain.model.PropertyType
import java.time.Instant

data class PropertyResponse(
    val id: String,
    val ownerId: String,
    val name: String,
    val type: PropertyType,
    val address: AddressResponse,
    val contactInfo: ContactInfoResponse,
    val description: String,
    val status: PropertyStatus,
    val timezone: String,
    val currency: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    data class AddressResponse(
        val street: String,
        val city: String,
        val state: String,
        val zipCode: String,
        val country: String,
        val latitude: Double?,
        val longitude: Double?
    )

    data class ContactInfoResponse(
        val phone: String,
        val email: String,
        val website: String?
    )

    companion object {
        fun from(property: Property): PropertyResponse = PropertyResponse(
            id = property.id,
            ownerId = property.ownerId,
            name = property.name,
            type = property.type,
            address = AddressResponse(
                street = property.address.street,
                city = property.address.city,
                state = property.address.state,
                zipCode = property.address.zipCode,
                country = property.address.country,
                latitude = property.address.latitude,
                longitude = property.address.longitude
            ),
            contactInfo = ContactInfoResponse(
                phone = property.contactInfo.phone,
                email = property.contactInfo.email,
                website = property.contactInfo.website
            ),
            description = property.description,
            status = property.status,
            timezone = property.timezone,
            currency = property.currency,
            createdAt = property.createdAt,
            updatedAt = property.updatedAt
        )
    }
}
