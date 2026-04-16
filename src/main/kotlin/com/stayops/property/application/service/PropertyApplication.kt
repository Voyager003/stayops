package com.stayops.property.application.service

import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PropertyApplication(
    private val propertyRepository: PropertyRepository,
    private val idGenerator: IdGenerator
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
            id = idGenerator.generate(),
            ownerId = ownerId,
            name = name,
            type = type,
            address = address,
            contactInfo = contactInfo,
            description = description,
            timezone = timezone,
            currency = currency
        )
        val saved = propertyRepository.save(property)

        log.info("숙소 생성: propertyId={}, ownerId={}, name={}", saved.id, ownerId, name)
        return saved
    }

    fun getProperty(id: String): Property =
        propertyRepository.findById(id)
            ?: throw NotFoundException("PROPERTY_NOT_FOUND", "숙소를 찾을 수 없습니다: $id")

    fun getAccessibleProperties(propertyIds: List<String>): List<Property> =
        propertyRepository.findByIds(propertyIds)

    fun getAllProperties(): List<Property> =
        propertyRepository.findAll()

    fun activateProperty(id: String): Property {
        val property = getProperty(id)
        val activated = property.activate()
        log.info("숙소 활성화: propertyId={}", id)
        return propertyRepository.save(activated)
    }

    fun deactivateProperty(id: String): Property {
        val property = getProperty(id)
        val deactivated = property.deactivate()
        log.info("숙소 비활성화: propertyId={}", id)
        return propertyRepository.save(deactivated)
    }

    fun updateProperty(
        id: String,
        name: String,
        description: String,
        address: Address,
        contactInfo: ContactInfo
    ): Property {
        val property = getProperty(id)
        val updated = property.updateInfo(name = name, description = description, address = address, contactInfo = contactInfo)
        log.info("숙소 수정: propertyId={}, name={}", id, name)
        return propertyRepository.save(updated)
    }
}
