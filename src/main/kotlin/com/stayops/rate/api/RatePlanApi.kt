package com.stayops.rate.api

import com.stayops.rate.api.dto.CreateRatePlanRequest
import com.stayops.rate.api.dto.DayOfWeekRuleRequest
import com.stayops.rate.api.dto.RatePlanResponse
import com.stayops.rate.api.dto.RatePreviewResponse
import com.stayops.rate.api.dto.UpdateRatePlanRequest
import com.stayops.rate.application.service.RatePlanApplication
import com.stayops.shared.security.PropertyAccessChecker
import com.stayops.rate.domain.model.DayOfWeekRate
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.DayOfWeek
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/properties/{pid}")
class RatePlanApi(
    private val ratePlanApplication: RatePlanApplication,
    private val propertyAccessChecker: PropertyAccessChecker
) {
    @PostMapping("/rate-plans")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRatePlan(
        @PathVariable pid: String,
        @RequestBody @Valid request: CreateRatePlanRequest
    ): RatePlanResponse {
        propertyAccessChecker.requireAccess(pid)
        val dateRange = if (request.dateRangeStart != null && request.dateRangeEnd != null) {
            DateRange.of(request.dateRangeStart, request.dateRangeEnd)
        } else null

        val dayOfWeekRules = request.dayOfWeekRules?.map { it.toDomain() }

        val ratePlan = ratePlanApplication.createRatePlan(
            propertyId = pid,
            roomTypeId = request.roomTypeId,
            name = request.name,
            type = request.type,
            dateRange = dateRange,
            dayOfWeekRules = dayOfWeekRules,
            channelCode = request.channelCode,
            price = Money.of(request.price),
            priority = request.priority
        )
        return RatePlanResponse.from(ratePlan)
    }

    @GetMapping("/rate-plans")
    fun getRatePlans(@PathVariable pid: String): List<RatePlanResponse> {
        propertyAccessChecker.requireAccess(pid)
        return ratePlanApplication.getRatePlans(pid).map { RatePlanResponse.from(it) }
    }

    @PutMapping("/rate-plans/{id}")
    fun updateRatePlan(
        @PathVariable pid: String,
        @PathVariable id: String,
        @RequestBody @Valid request: UpdateRatePlanRequest
    ): RatePlanResponse {
        propertyAccessChecker.requireAccess(pid)
        val dateRange = if (request.dateRangeStart != null && request.dateRangeEnd != null) {
            DateRange.of(request.dateRangeStart, request.dateRangeEnd)
        } else null
        val dayOfWeekRules = request.dayOfWeekRules?.map { it.toDomain() }
        val ratePlan = ratePlanApplication.updateRatePlan(
            id = id,
            name = request.name,
            dateRange = dateRange,
            dayOfWeekRules = dayOfWeekRules,
            channelCode = request.channelCode,
            price = Money.of(request.price),
            priority = request.priority
        )
        return RatePlanResponse.from(ratePlan)
    }

    @PatchMapping("/rate-plans/{id}/activate")
    fun activateRatePlan(
        @PathVariable pid: String,
        @PathVariable id: String
    ): RatePlanResponse {
        propertyAccessChecker.requireAccess(pid)
        return RatePlanResponse.from(ratePlanApplication.activateRatePlan(id))
    }

    @PatchMapping("/rate-plans/{id}/deactivate")
    fun deactivateRatePlan(
        @PathVariable pid: String,
        @PathVariable id: String
    ): RatePlanResponse {
        propertyAccessChecker.requireAccess(pid)
        return RatePlanResponse.from(ratePlanApplication.deactivateRatePlan(id))
    }

    @DeleteMapping("/rate-plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRatePlan(
        @PathVariable pid: String,
        @PathVariable id: String
    ) {
        propertyAccessChecker.requireAccess(pid)
        ratePlanApplication.deleteRatePlan(id)
    }

    @GetMapping("/rates/preview")
    fun previewRates(
        @PathVariable pid: String,
        @RequestParam roomTypeId: String,
        @RequestParam basePrice: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(required = false) channelCode: String?
    ): List<RatePreviewResponse> {
        propertyAccessChecker.requireAccess(pid)
        return ratePlanApplication.previewRates(pid, roomTypeId, Money.of(basePrice), startDate, endDate, channelCode)
            .map { RatePreviewResponse.from(it) }
    }

    private fun DayOfWeekRuleRequest.toDomain() = DayOfWeekRate(
        daysOfWeek = daysOfWeek.map { DayOfWeek.valueOf(it) }.toSet(),
        price = Money.of(price)
    )
}
