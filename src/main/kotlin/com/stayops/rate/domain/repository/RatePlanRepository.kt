package com.stayops.rate.domain.repository

import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus

interface RatePlanRepository {
    fun save(ratePlan: RatePlan): RatePlan
    fun findById(id: String): RatePlan?
    fun findByPropertyIdAndRoomTypeIdAndStatus(
        propertyId: String, roomTypeId: String, status: RatePlanStatus
    ): List<RatePlan>
    fun findByPropertyId(propertyId: String): List<RatePlan>
    fun deleteById(id: String)
}
