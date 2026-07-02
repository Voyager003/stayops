package com.stayops.property.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.tables.records.PropertiesRecord
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyStatus
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.config.RdbPersistence
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbPropertyRepository(
    private val dsl: DSLContext
) : PropertyRepository {

    override fun save(property: Property): Property {
        dsl.insertInto(PROPERTIES)
            .set(PROPERTIES.ID, property.id)
            .set(PROPERTIES.OWNER_ID, property.ownerId)
            .set(PROPERTIES.NAME, property.name)
            .set(PROPERTIES.TYPE, property.type.name)
            .set(PROPERTIES.ADDRESS_STREET, property.address.street)
            .set(PROPERTIES.ADDRESS_CITY, property.address.city)
            .set(PROPERTIES.ADDRESS_STATE, property.address.state)
            .set(PROPERTIES.ADDRESS_ZIP_CODE, property.address.zipCode)
            .set(PROPERTIES.ADDRESS_COUNTRY, property.address.country)
            .set(PROPERTIES.ADDRESS_LATITUDE, property.address.latitude)
            .set(PROPERTIES.ADDRESS_LONGITUDE, property.address.longitude)
            .set(PROPERTIES.CONTACT_PHONE, property.contactInfo.phone)
            .set(PROPERTIES.CONTACT_EMAIL, property.contactInfo.email)
            .set(PROPERTIES.CONTACT_WEBSITE, property.contactInfo.website)
            .set(PROPERTIES.DESCRIPTION, property.description)
            .set(PROPERTIES.STATUS, property.status.name)
            .set(PROPERTIES.TIMEZONE, property.timezone)
            .set(PROPERTIES.CURRENCY, property.currency)
            .set(PROPERTIES.VERSION, property.version)
            .set(PROPERTIES.CREATED_AT, property.createdAt.toOffsetDateTime())
            .set(PROPERTIES.UPDATED_AT, property.updatedAt.toOffsetDateTime())
            .onConflict(PROPERTIES.ID)
            .doUpdate()
            .set(PROPERTIES.OWNER_ID, property.ownerId)
            .set(PROPERTIES.NAME, property.name)
            .set(PROPERTIES.TYPE, property.type.name)
            .set(PROPERTIES.ADDRESS_STREET, property.address.street)
            .set(PROPERTIES.ADDRESS_CITY, property.address.city)
            .set(PROPERTIES.ADDRESS_STATE, property.address.state)
            .set(PROPERTIES.ADDRESS_ZIP_CODE, property.address.zipCode)
            .set(PROPERTIES.ADDRESS_COUNTRY, property.address.country)
            .set(PROPERTIES.ADDRESS_LATITUDE, property.address.latitude)
            .set(PROPERTIES.ADDRESS_LONGITUDE, property.address.longitude)
            .set(PROPERTIES.CONTACT_PHONE, property.contactInfo.phone)
            .set(PROPERTIES.CONTACT_EMAIL, property.contactInfo.email)
            .set(PROPERTIES.CONTACT_WEBSITE, property.contactInfo.website)
            .set(PROPERTIES.DESCRIPTION, property.description)
            .set(PROPERTIES.STATUS, property.status.name)
            .set(PROPERTIES.TIMEZONE, property.timezone)
            .set(PROPERTIES.CURRENCY, property.currency)
            .set(PROPERTIES.VERSION, property.version)
            .set(PROPERTIES.CREATED_AT, property.createdAt.toOffsetDateTime())
            .set(PROPERTIES.UPDATED_AT, property.updatedAt.toOffsetDateTime())
            .execute()

        return findById(property.id) ?: property
    }

    override fun findById(id: String): Property? =
        dsl.selectFrom(PROPERTIES)
            .where(PROPERTIES.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    override fun findByOwnerId(ownerId: String): List<Property> =
        dsl.selectFrom(PROPERTIES)
            .where(PROPERTIES.OWNER_ID.eq(ownerId))
            .orderBy(PROPERTIES.ID.asc())
            .fetch { it.toDomain() }

    override fun findByIds(ids: List<String>): List<Property> {
        if (ids.isEmpty()) return emptyList()

        return dsl.selectFrom(PROPERTIES)
            .where(PROPERTIES.ID.`in`(ids))
            .orderBy(PROPERTIES.ID.asc())
            .fetch { it.toDomain() }
    }

    override fun findAll(): List<Property> =
        dsl.selectFrom(PROPERTIES)
            .orderBy(PROPERTIES.ID.asc())
            .fetch { it.toDomain() }

    private fun PropertiesRecord.toDomain(): Property =
        Property.reconstitute(
            id = get(PROPERTIES.ID),
            ownerId = get(PROPERTIES.OWNER_ID),
            name = get(PROPERTIES.NAME),
            type = PropertyType.valueOf(get(PROPERTIES.TYPE)),
            address = Address.of(
                street = get(PROPERTIES.ADDRESS_STREET),
                city = get(PROPERTIES.ADDRESS_CITY),
                state = get(PROPERTIES.ADDRESS_STATE),
                zipCode = get(PROPERTIES.ADDRESS_ZIP_CODE),
                country = get(PROPERTIES.ADDRESS_COUNTRY),
                latitude = get(PROPERTIES.ADDRESS_LATITUDE),
                longitude = get(PROPERTIES.ADDRESS_LONGITUDE)
            ),
            contactInfo = ContactInfo.of(
                phone = get(PROPERTIES.CONTACT_PHONE),
                email = get(PROPERTIES.CONTACT_EMAIL),
                website = get(PROPERTIES.CONTACT_WEBSITE)
            ),
            description = get(PROPERTIES.DESCRIPTION),
            status = PropertyStatus.valueOf(get(PROPERTIES.STATUS)),
            timezone = get(PROPERTIES.TIMEZONE),
            currency = get(PROPERTIES.CURRENCY),
            version = get(PROPERTIES.VERSION),
            createdAt = get(PROPERTIES.CREATED_AT).toInstant(),
            updatedAt = get(PROPERTIES.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
