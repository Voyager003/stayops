package com.stayops.property.application.service

import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PropertyApplication(
    private val propertyRepository: PropertyRepository
) {
    fun createProperty(
        ownerId: String,
        name: String,
        type: PropertyType,
        address: Address,
        contactInfo: ContactInfo,
        description: String,
        timezone: String = "Asia/Seoul",
        currency: String = "KRW"
    ): Property {
        val property = Property.create(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            name = name,
            type = type,
            address = address,
            contactInfo = contactInfo,
            description = description,
            timezone = timezone,
            currency = currency
        )
        return propertyRepository.save(property)
    }

    fun getProperty(id: String): Property =
        propertyRepository.findById(id)
            ?: throw NotFoundException("PROPERTY_NOT_FOUND", "숙소를 찾을 수 없습니다: $id")

    fun getAllProperties(): List<Property> =
        propertyRepository.findAll()

    fun updateProperty(
        id: String,
        name: String,
        description: String,
        address: Address,
        contactInfo: ContactInfo
    ): Property {
        val property = getProperty(id)
        val updated = property.updateInfo(name = name, description = description, address = address, contactInfo = contactInfo)
        return propertyRepository.save(updated)
    }
}
