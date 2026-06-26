package com.stayops.guest.infrastructure.persistence.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.guest.domain.model.GuestTier
import com.stayops.guest.infrastructure.persistence.GuestDocument
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

@MongoPersistence
interface GuestMongoDao : MongoRepository<GuestDocument, String> {
    fun findByPropertyIdAndPhone(propertyId: String, phone: String): GuestDocument?
    fun findByPropertyId(propertyId: String): List<GuestDocument>
    fun findByPropertyIdAndTier(propertyId: String, tier: GuestTier): List<GuestDocument>

    @Query("{ 'propertyId': ?0, 'name': { '\$regex': ?1 } }")
    fun findByPropertyIdAndNameContaining(propertyId: String, name: String): List<GuestDocument>
}
