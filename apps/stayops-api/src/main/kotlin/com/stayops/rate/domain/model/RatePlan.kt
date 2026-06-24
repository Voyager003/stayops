package com.stayops.rate.domain.model

import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import java.time.Instant
import java.time.LocalDate

class RatePlan private constructor(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val name: String,
    val type: RatePlanType,
    val dateRange: DateRange?,
    val dayOfWeekRules: List<DayOfWeekRate>?,
    val channelCode: String?,
    val price: Money,
    val priority: Int,
    val status: RatePlanStatus,
    val version: Long?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun activate(): RatePlan = copyState(status = RatePlanStatus.ACTIVE, updatedAt = Instant.now())

    fun deactivate(): RatePlan = copyState(status = RatePlanStatus.INACTIVE, updatedAt = Instant.now())

    fun updateInfo(
        name: String,
        dateRange: DateRange?,
        dayOfWeekRules: List<DayOfWeekRate>?,
        channelCode: String?,
        price: Money,
        priority: Int
    ): RatePlan {
        require(name.isNotBlank()) { "요금제 이름은 공백일 수 없습니다." }
        require(priority > 0) { "우선순위는 0보다 커야 합니다: $priority" }
        return copyState(
            name = name,
            dateRange = dateRange,
            dayOfWeekRules = dayOfWeekRules,
            channelCode = channelCode,
            price = price,
            priority = priority,
            updatedAt = Instant.now()
        )
    }

    fun priceForDate(date: LocalDate): Money {
        val dayOfWeekPrice = dayOfWeekRules
            ?.firstOrNull { date.dayOfWeek in it.daysOfWeek }
            ?.price
        return dayOfWeekPrice ?: price
    }

    fun isApplicableTo(date: LocalDate, channelCode: String?): Boolean {
        if (status != RatePlanStatus.ACTIVE) return false
        if (dateRange != null) {
            val inRange = !date.isBefore(dateRange.checkIn) && date.isBefore(dateRange.checkOut)
            if (!inRange) return false
        }
        if (type == RatePlanType.CHANNEL_SPECIFIC && this.channelCode != channelCode) return false
        return true
    }

    private fun copyState(
        name: String = this.name,
        dateRange: DateRange? = this.dateRange,
        dayOfWeekRules: List<DayOfWeekRate>? = this.dayOfWeekRules,
        channelCode: String? = this.channelCode,
        price: Money = this.price,
        priority: Int = this.priority,
        status: RatePlanStatus = this.status,
        updatedAt: Instant = this.updatedAt
    ): RatePlan = RatePlan(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        name = name,
        type = type,
        dateRange = dateRange,
        dayOfWeekRules = dayOfWeekRules,
        channelCode = channelCode,
        price = price,
        priority = priority,
        status = status,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun equals(other: Any?): Boolean =
        this === other || (other is RatePlan && id == other.id)

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "RatePlan(id=$id, propertyId=$propertyId, roomTypeId=$roomTypeId, name=$name, type=$type, status=$status)"

    companion object {
        fun create(
            id: String,
            propertyId: String,
            roomTypeId: String,
            name: String,
            type: RatePlanType,
            dateRange: DateRange?,
            dayOfWeekRules: List<DayOfWeekRate>?,
            channelCode: String?,
            price: Money,
            priority: Int
        ): RatePlan {
            require(name.isNotBlank()) { "요금제 이름은 공백일 수 없습니다." }
            require(priority > 0) { "우선순위는 0보다 커야 합니다: $priority" }
            val now = Instant.now()
            return RatePlan(
                id = id,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                name = name,
                type = type,
                dateRange = dateRange,
                dayOfWeekRules = dayOfWeekRules,
                channelCode = channelCode,
                price = price,
                priority = priority,
                status = RatePlanStatus.ACTIVE,
                version = null,
                createdAt = now,
                updatedAt = now
            )
        }

        fun reconstitute(
            id: String,
            propertyId: String,
            roomTypeId: String,
            name: String,
            type: RatePlanType,
            dateRange: DateRange?,
            dayOfWeekRules: List<DayOfWeekRate>?,
            channelCode: String?,
            price: Money,
            priority: Int,
            status: RatePlanStatus,
            version: Long?,
            createdAt: Instant,
            updatedAt: Instant
        ): RatePlan = RatePlan(
            id = id,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            name = name,
            type = type,
            dateRange = dateRange,
            dayOfWeekRules = dayOfWeekRules,
            channelCode = channelCode,
            price = price,
            priority = priority,
            status = status,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
