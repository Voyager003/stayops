package com.stayops.property.infrastructure.persistence.mongo.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.property.infrastructure.persistence.mongo.document.PropertyDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface PropertyMongoDao : MongoRepository<PropertyDocument, String> {
    fun findByOwnerId(ownerId: String): List<PropertyDocument>
}
