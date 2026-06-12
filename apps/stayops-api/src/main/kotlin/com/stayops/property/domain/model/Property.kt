package com.stayops.property.domain.model

import java.time.Instant

class Property private constructor(
    val id: String,
    val ownerId: String,
    val name: String,
    val type: PropertyType,
    val address: Address,
    val contactInfo: ContactInfo,
    val description: String,
    val status: PropertyStatus,
    val timezone: String,
    val currency: String,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun activate(): Property {
        require(status == PropertyStatus.INACTIVE) {
            "INACTIVE 상태에서만 활성화할 수 있습니다: $status"
        }
        return copyState(status = PropertyStatus.ACTIVE, updatedAt = Instant.now())
    }

    fun deactivate(): Property {
        require(status == PropertyStatus.ACTIVE) {
            "ACTIVE 상태에서만 비활성화할 수 있습니다: $status"
        }
        return copyState(status = PropertyStatus.INACTIVE, updatedAt = Instant.now())
    }

    fun suspend(): Property {
        require(status == PropertyStatus.ACTIVE) {
            "ACTIVE 상태에서만 정지할 수 있습니다: $status"
        }
        return copyState(status = PropertyStatus.SUSPENDED, updatedAt = Instant.now())
    }

    fun isBookable(): Boolean = status == PropertyStatus.ACTIVE

    fun updateInfo(
        name: String = this.name,
        description: String = this.description,
        address: Address = this.address,
        contactInfo: ContactInfo = this.contactInfo
    ): Property {
        require(name.isNotBlank()) { "숙소명은 비어있을 수 없습니다" }
        return copyState(
            name = name,
            description = description,
            address = address,
            contactInfo = contactInfo,
            updatedAt = Instant.now()
        )
    }

    private fun copyState(
        id: String = this.id,
        ownerId: String = this.ownerId,
        name: String = this.name,
        type: PropertyType = this.type,
        address: Address = this.address,
        contactInfo: ContactInfo = this.contactInfo,
        description: String = this.description,
        status: PropertyStatus = this.status,
        timezone: String = this.timezone,
        currency: String = this.currency,
        version: Long = this.version,
        createdAt: Instant = this.createdAt,
        updatedAt: Instant = this.updatedAt
    ): Property = Property(
        id = id,
        ownerId = ownerId,
        name = name,
        type = type,
        address = address,
        contactInfo = contactInfo,
        description = description,
        status = status,
        timezone = timezone,
        currency = currency,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun equals(other: Any?): Boolean =
        this === other || (other is Property && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Property(id=$id, ownerId=$ownerId, name=$name, type=$type, status=$status)"

    companion object {
        fun create(
            id: String,
            ownerId: String,
            name: String,
            type: PropertyType,
            address: Address,
            contactInfo: ContactInfo,
            description: String,
            timezone: String = "Asia/Seoul",
            currency: String = "KRW"
        ): Property {
            require(name.isNotBlank()) { "숙소명은 비어있을 수 없습니다" }
            val now = Instant.now()
            return Property(
                id = id,
                ownerId = ownerId,
                name = name,
                type = type,
                address = address,
                contactInfo = contactInfo,
                description = description,
                status = PropertyStatus.INACTIVE,
                timezone = timezone,
                currency = currency,
                version = 0,
                createdAt = now,
                updatedAt = now
            )
        }

        // Reconstitutes a Property from persistence — bypasses business creation logic
        fun reconstitute(
            id: String,
            ownerId: String,
            name: String,
            type: PropertyType,
            address: Address,
            contactInfo: ContactInfo,
            description: String,
            status: PropertyStatus,
            timezone: String,
            currency: String,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): Property = Property(
            id = id,
            ownerId = ownerId,
            name = name,
            type = type,
            address = address,
            contactInfo = contactInfo,
            description = description,
            status = status,
            timezone = timezone,
            currency = currency,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
