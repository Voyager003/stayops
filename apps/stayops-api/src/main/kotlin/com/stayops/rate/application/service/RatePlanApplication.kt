package com.stayops.rate.application.service

import com.stayops.rate.domain.model.DayOfWeekRate
import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.model.RatePlanType
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RatePlanApplication(
    private val ratePlanRepository: RatePlanRepository,
    private val rateResolverService: RateResolverService,
    private val idGenerator: IdGenerator
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
            id = idGenerator.generate(),
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
        val saved = ratePlanRepository.save(ratePlan)
        log.info("요금제 생성: ratePlanId={}, propertyId={}, name={}", saved.id, propertyId, name)
        return saved
    }

    fun getRatePlans(propertyId: String): List<RatePlan> =
        ratePlanRepository.findByPropertyId(propertyId)

    fun updateRatePlan(
        propertyId: String,
        id: String,
        name: String,
        dateRange: DateRange?,
        dayOfWeekRules: List<DayOfWeekRate>?,
        channelCode: String?,
        price: Money,
        priority: Int
    ): RatePlan {
        val ratePlan = findRatePlanWithOwnership(propertyId, id)
        val updated = ratePlan.updateInfo(name, dateRange, dayOfWeekRules, channelCode, price, priority)
        val saved = ratePlanRepository.save(updated)
        log.info("요금제 수정: ratePlanId={}, propertyId={}", id, propertyId)
        return saved
    }

    fun activateRatePlan(propertyId: String, id: String): RatePlan {
        val ratePlan = findRatePlanWithOwnership(propertyId, id)
        log.info("요금제 활성화: ratePlanId={}, propertyId={}", id, propertyId)
        return ratePlanRepository.save(ratePlan.activate())
    }

    fun deactivateRatePlan(propertyId: String, id: String): RatePlan {
        val ratePlan = findRatePlanWithOwnership(propertyId, id)
        log.info("요금제 비활성화: ratePlanId={}, propertyId={}", id, propertyId)
        return ratePlanRepository.save(ratePlan.deactivate())
    }

    private fun findRatePlanWithOwnership(propertyId: String, id: String): RatePlan {
        val ratePlan = ratePlanRepository.findById(id)
            ?: throw NotFoundException("RATE_PLAN_NOT_FOUND", "요금제를 찾을 수 없습니다: $id")
        require(ratePlan.propertyId == propertyId) { "해당 숙소의 요금제가 아닙니다." }
        return ratePlan
    }

    fun deleteRatePlan(id: String) {
        ratePlanRepository.findById(id)
            ?: throw NotFoundException("RATE_PLAN_NOT_FOUND", "요금제를 찾을 수 없습니다: $id")
        ratePlanRepository.deleteById(id)
        log.info("요금제 삭제: ratePlanId={}", id)
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
                price = rateResolverService.resolve(ratePlans, basePrice, date, channelCode)
            )
        }
    }

    data class DailyRate(val date: LocalDate, val price: Money)
}
