package com.stayops.rate.application.service

import com.stayops.rate.domain.model.DayOfWeekRate
import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.model.RatePlanType
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolver
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class RatePlanApplication(
    private val ratePlanRepository: RatePlanRepository
) {
    private val rateResolver = RateResolver()

    fun createRatePlan(
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
        val ratePlan = RatePlan.create(
            id = UUID.randomUUID().toString(),
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            name = name,
            type = type,
            dateRange = dateRange,
            dayOfWeekRules = dayOfWeekRules,
            channelCode = channelCode,
            price = price,
            priority = priority
        )
        return ratePlanRepository.save(ratePlan)
    }

    fun getRatePlans(propertyId: String): List<RatePlan> =
        ratePlanRepository.findByPropertyId(propertyId)

    fun deleteRatePlan(id: String) {
        ratePlanRepository.findById(id)
            ?: throw NotFoundException("RATE_PLAN_NOT_FOUND", "요금제를 찾을 수 없습니다: $id")
        ratePlanRepository.deleteById(id)
    }

    fun previewRates(
        propertyId: String,
        roomTypeId: String,
        basePrice: Money,
        startDate: LocalDate,
        endDate: LocalDate,
        channelCode: String?
    ): List<DailyRate> {
        val ratePlans = ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
            propertyId, roomTypeId, RatePlanStatus.ACTIVE
        )
        val dateRange = DateRange.of(startDate, endDate)
        return dateRange.allDates().map { date ->
            DailyRate(
                date = date,
                price = rateResolver.resolve(ratePlans, basePrice, date, channelCode)
            )
        }
    }

    data class DailyRate(val date: LocalDate, val price: Money)
}
