package com.stayops.rate.api.dto

import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.model.RatePlanType

data class RatePlanResponse(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val name: String,
    val type: RatePlanType,
    val dateRangeStart: String?,
    val dateRangeEnd: String?,
    val dayOfWeekRules: List<DayOfWeekRuleResponse>?,
    val channelCode: String?,
    val price: Long,
    val priority: Int,
    val status: RatePlanStatus
) {
    companion object {
        fun from(ratePlan: RatePlan) = RatePlanResponse(
            id = ratePlan.id,
            propertyId = ratePlan.propertyId,
            roomTypeId = ratePlan.roomTypeId,
            name = ratePlan.name,
            type = ratePlan.type,
            dateRangeStart = ratePlan.dateRange?.checkIn?.toString(),
            dateRangeEnd = ratePlan.dateRange?.checkOut?.toString(),
            dayOfWeekRules = ratePlan.dayOfWeekRules?.map { DayOfWeekRuleResponse(it.daysOfWeek.map { d -> d.name }, it.price.amount.toLong()) },
            channelCode = ratePlan.channelCode,
            price = ratePlan.price.amount.toLong(),
            priority = ratePlan.priority,
            status = ratePlan.status
        )
    }
}

data class DayOfWeekRuleResponse(
    val daysOfWeek: List<String>,
    val price: Long
)
