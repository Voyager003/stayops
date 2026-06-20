package com.stayops.property.infrastructure.persistence.dao

import com.stayops.property.infrastructure.persistence.PropertyDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface PropertyMongoDao : MongoRepository<PropertyDocument, String> {
    fun findByOwnerId(ownerId: String): List<PropertyDocument>
}
