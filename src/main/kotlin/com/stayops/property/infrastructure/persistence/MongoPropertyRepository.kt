package com.stayops.property.infrastructure.persistence

import com.stayops.property.domain.model.Property
import com.stayops.property.domain.repository.PropertyRepository
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MongoPropertyRepository(
    private val mongo: PropertyMongoDataRepository
) : PropertyRepository {

    override fun save(property: Property): Property =
        mongo.save(PropertyDocument.from(property)).toDomain()

    override fun findById(id: String): Property? =
        mongo.findByIdOrNull(id)?.toDomain()

    override fun findByOwnerId(ownerId: String): List<Property> =
        mongo.findByOwnerId(ownerId).map { it.toDomain() }

    override fun findByIds(ids: List<String>): List<Property> =
        mongo.findAllById(ids).map { it.toDomain() }

    override fun findAll(): List<Property> =
        mongo.findAll().map { it.toDomain() }
}

interface PropertyMongoDataRepository : MongoRepository<PropertyDocument, String> {
    fun findByOwnerId(ownerId: String): List<PropertyDocument>
}
