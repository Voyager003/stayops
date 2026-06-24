package com.stayops.property.application.dto

import com.stayops.property.domain.model.Property

data class CreatePropertyCommand(
    val ownerId: String,
    val name: String,
    val type: String,
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String,
    val latitude: Double?,
    val longitude: Double?,
    val phone: String,
    val email: String,
    val website: String?,
    val description: String,
    val timezone: String,
    val currency: String
)

data class UpdatePropertyCommand(
    val id: String,
    val name: String,
    val description: String,
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String,
    val latitude: Double?,
    val longitude: Double?,
    val phone: String,
    val email: String,
    val website: String?
)

data class PropertyView(
    val id: String,
    val ownerId: String,
    val name: String,
    val type: String,
    val address: AddressView,
    val contactInfo: ContactInfoView,
    val description: String,
    val status: String,
    val timezone: String,
    val currency: String,
    val createdAt: java.time.Instant,
    val updatedAt: java.time.Instant
) {
    data class AddressView(
        val street: String,
        val city: String,
        val state: String,
        val zipCode: String,
        val country: String,
        val latitude: Double?,
        val longitude: Double?
    )

    data class ContactInfoView(
        val phone: String,
        val email: String,
        val website: String?
    )

    companion object {
        fun from(property: Property): PropertyView = PropertyView(
            id = property.id,
            ownerId = property.ownerId,
            name = property.name,
            type = property.type.name,
            address = AddressView(
                street = property.address.street,
                city = property.address.city,
                state = property.address.state,
                zipCode = property.address.zipCode,
                country = property.address.country,
                latitude = property.address.latitude,
                longitude = property.address.longitude
            ),
            contactInfo = ContactInfoView(
                phone = property.contactInfo.phone,
                email = property.contactInfo.email,
                website = property.contactInfo.website
            ),
            description = property.description,
            status = property.status.name,
            timezone = property.timezone,
            currency = property.currency,
            createdAt = property.createdAt,
            updatedAt = property.updatedAt
        )
    }
}
