package com.stayops.rate.infrastructure.persistence.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.infrastructure.persistence.RatePlanDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface RatePlanMongoDao : MongoRepository<RatePlanDocument, String> {
    fun findByPropertyIdAndRoomTypeIdAndStatus(
        propertyId: String,
        roomTypeId: String,
        status: RatePlanStatus
    ): List<RatePlanDocument>

    fun findByPropertyId(propertyId: String): List<RatePlanDocument>
}
