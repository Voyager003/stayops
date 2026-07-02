package com.stayops.rate.infrastructure.persistence.mongo.document

import com.stayops.rate.domain.model.DayOfWeekRate
import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.model.RatePlanType
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@Document("rate_plans")
@CompoundIndex(def = "{'propertyId': 1, 'roomTypeId': 1, 'status': 1, 'priority': -1}")
data class RatePlanDocument(
    @Id val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val name: String,
    val type: RatePlanType,
    val dateRangeStart: String?,
    val dateRangeEnd: String?,
    val dayOfWeekRules: List<DayOfWeekRuleDocument>?,
    val channelCode: String?,
    val priceAmount: Long,
    val priority: Int,
    val status: RatePlanStatus,
    @Version val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toDomain(): RatePlan = RatePlan.reconstitute(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        name = name,
        type = type,
        dateRange = if (dateRangeStart != null && dateRangeEnd != null) {
            DateRange.of(LocalDate.parse(dateRangeStart), LocalDate.parse(dateRangeEnd))
        } else null,
        dayOfWeekRules = dayOfWeekRules?.map { it.toDomain() },
        channelCode = channelCode,
        price = Money.of(priceAmount),
        priority = priority,
        status = status,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(ratePlan: RatePlan): RatePlanDocument = RatePlanDocument(
            id = ratePlan.id,
            propertyId = ratePlan.propertyId,
            roomTypeId = ratePlan.roomTypeId,
            name = ratePlan.name,
            type = ratePlan.type,
            dateRangeStart = ratePlan.dateRange?.checkIn?.toString(),
            dateRangeEnd = ratePlan.dateRange?.checkOut?.toString(),
            dayOfWeekRules = ratePlan.dayOfWeekRules?.map { DayOfWeekRuleDocument.from(it) },
            channelCode = ratePlan.channelCode,
            priceAmount = ratePlan.price.amount.toLong(),
            priority = ratePlan.priority,
            status = ratePlan.status,
            version = ratePlan.version,
            createdAt = ratePlan.createdAt,
            updatedAt = ratePlan.updatedAt
        )
    }
}

data class DayOfWeekRuleDocument(
    val daysOfWeek: List<String>,
    val priceAmount: Long
) {
    fun toDomain(): DayOfWeekRate = DayOfWeekRate(
        daysOfWeek = daysOfWeek.map { DayOfWeek.valueOf(it) }.toSet(),
        price = Money.of(priceAmount)
    )

    companion object {
        fun from(rule: DayOfWeekRate): DayOfWeekRuleDocument = DayOfWeekRuleDocument(
            daysOfWeek = rule.daysOfWeek.map { it.name },
            priceAmount = rule.price.amount.toLong()
        )
    }
}
