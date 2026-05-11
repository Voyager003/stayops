package com.stayops.rate.infrastructure.persistence

import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import jakarta.annotation.PostConstruct
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MongoRatePlanRepository(
    private val mongo: RatePlanMongoDataRepository,
    private val mongoTemplate: MongoTemplate
) : RatePlanRepository {

    @PostConstruct
    fun createIndexes() {
        mongoTemplate.indexOps(RatePlanDocument::class.java).createIndex(
            CompoundIndexDefinition(
                org.bson.Document(
                    mapOf("propertyId" to 1, "roomTypeId" to 1, "status" to 1, "priority" to -1)
                )
            )
        )
    }

    override fun save(ratePlan: RatePlan): RatePlan =
        mongo.save(RatePlanDocument.from(ratePlan)).toDomain()

    override fun findById(id: String): RatePlan? =
        mongo.findByIdOrNull(id)?.toDomain()

    override fun findByPropertyIdAndRoomTypeIdAndStatus(
        propertyId: String, roomTypeId: String, status: RatePlanStatus
    ): List<RatePlan> =
        mongo.findByPropertyIdAndRoomTypeIdAndStatus(propertyId, roomTypeId, status)
            .map { it.toDomain() }

    override fun findByPropertyId(propertyId: String): List<RatePlan> =
        mongo.findByPropertyId(propertyId).map { it.toDomain() }

    override fun deleteById(id: String) = mongo.deleteById(id)
}

interface RatePlanMongoDataRepository : MongoRepository<RatePlanDocument, String> {
    fun findByPropertyIdAndRoomTypeIdAndStatus(
        propertyId: String, roomTypeId: String, status: RatePlanStatus
    ): List<RatePlanDocument>

    fun findByPropertyId(propertyId: String): List<RatePlanDocument>
}
