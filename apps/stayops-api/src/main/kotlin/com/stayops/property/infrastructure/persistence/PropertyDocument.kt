package com.stayops.property.infrastructure.persistence

import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyStatus
import com.stayops.property.domain.model.PropertyType
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("properties")
data class PropertyDocument(
    @Id val id: String,
    val ownerId: String,
    val name: String,
    val type: PropertyType,
    val address: AddressData,
    val contactInfo: ContactInfoData,
    val description: String,
    val status: PropertyStatus,
    val timezone: String,
    val currency: String,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    data class AddressData(
        val street: String,
        val city: String,
        val state: String,
        val zipCode: String,
        val country: String,
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    data class ContactInfoData(
        val phone: String,
        val email: String,
        val website: String?
    )

    fun toDomain(): Property = Property.reconstitute(
        id = id,
        ownerId = ownerId,
        name = name,
        type = type,
        address = Address.of(address.street, address.city, address.state, address.zipCode, address.country, address.latitude, address.longitude),
        contactInfo = ContactInfo.of(contactInfo.phone, contactInfo.email, contactInfo.website),
        description = description,
        status = status,
        timezone = timezone,
        currency = currency,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(property: Property): PropertyDocument = PropertyDocument(
            id = property.id,
            ownerId = property.ownerId,
            name = property.name,
            type = property.type,
            address = AddressData(
                street = property.address.street,
                city = property.address.city,
                state = property.address.state,
                zipCode = property.address.zipCode,
                country = property.address.country,
                latitude = property.address.latitude,
                longitude = property.address.longitude
            ),
            contactInfo = ContactInfoData(
                phone = property.contactInfo.phone,
                email = property.contactInfo.email,
                website = property.contactInfo.website
            ),
            description = property.description,
            status = property.status,
            timezone = property.timezone,
            currency = property.currency,
            version = property.version,
            createdAt = property.createdAt,
            updatedAt = property.updatedAt
        )
    }
}
