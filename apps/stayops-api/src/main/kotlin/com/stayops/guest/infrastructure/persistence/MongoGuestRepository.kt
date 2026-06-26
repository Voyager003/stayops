package com.stayops.guest.infrastructure.persistence

import com.stayops.shared.config.MongoPersistence

import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.model.GuestTier
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.guest.infrastructure.persistence.dao.GuestMongoDao
import jakarta.annotation.PostConstruct
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@MongoPersistence
@Repository
class MongoGuestRepository(
    private val mongo: GuestMongoDao,
    private val mongoTemplate: MongoTemplate
) : GuestRepository {

    @PostConstruct
    fun createIndexes() {
        mongoTemplate.indexOps(GuestDocument::class.java).createIndex(
            CompoundIndexDefinition(
                org.bson.Document(mapOf("propertyId" to 1, "phone" to 1))
            ).unique()
        )
    }

    override fun save(guest: Guest): Guest =
        mongo.save(GuestDocument.from(guest)).toDomain()

    override fun findById(id: String): Guest? =
        mongo.findByIdOrNull(id)?.toDomain()

    override fun findByPropertyIdAndPhone(propertyId: String, phone: String): Guest? =
        mongo.findByPropertyIdAndPhone(propertyId, phone)?.toDomain()

    override fun findByPropertyId(propertyId: String): List<Guest> =
        mongo.findByPropertyId(propertyId).map { it.toDomain() }

    override fun findByPropertyIdAndTier(propertyId: String, tier: GuestTier): List<Guest> =
        mongo.findByPropertyIdAndTier(propertyId, tier).map { it.toDomain() }

    override fun findByPropertyIdAndNameContaining(propertyId: String, name: String): List<Guest> =
        mongo.findByPropertyIdAndNameContaining(propertyId, name).map { it.toDomain() }
}
