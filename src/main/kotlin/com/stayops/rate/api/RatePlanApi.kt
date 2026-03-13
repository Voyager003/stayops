package com.stayops.rate.api

import com.stayops.rate.api.dto.CreateRatePlanRequest
import com.stayops.rate.api.dto.DayOfWeekRuleRequest
import com.stayops.rate.api.dto.RatePlanResponse
import com.stayops.rate.api.dto.RatePreviewResponse
import com.stayops.rate.application.service.RatePlanApplication
import com.stayops.rate.domain.model.DayOfWeekRate
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/properties/{pid}")
class RatePlanApi(
    private val ratePlanApplication: RatePlanApplication
) {
    @PostMapping("/rate-plans")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRatePlan(
        @PathVariable pid: String,
        @RequestBody @Valid request: CreateRatePlanRequest
    ): RatePlanResponse {
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
    fun getRatePlans(@PathVariable pid: String): List<RatePlanResponse> =
        ratePlanApplication.getRatePlans(pid).map { RatePlanResponse.from(it) }

    @DeleteMapping("/rate-plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRatePlan(
        @PathVariable pid: String,
        @PathVariable id: String
    ) = ratePlanApplication.deleteRatePlan(id)

    @GetMapping("/rates/preview")
    fun previewRates(
        @PathVariable pid: String,
        @RequestParam roomTypeId: String,
        @RequestParam basePrice: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(required = false) channelCode: String?
    ): List<RatePreviewResponse> =
        ratePlanApplication.previewRates(pid, roomTypeId, Money.of(basePrice), startDate, endDate, channelCode)
            .map { RatePreviewResponse.from(it) }

    private fun DayOfWeekRuleRequest.toDomain() = DayOfWeekRate(
        daysOfWeek = daysOfWeek.map { DayOfWeek.valueOf(it) }.toSet(),
        price = Money.of(price)
    )
}
