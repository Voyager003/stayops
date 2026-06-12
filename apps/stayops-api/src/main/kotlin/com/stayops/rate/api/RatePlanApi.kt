package com.stayops.rate.api

import com.stayops.rate.api.dto.CreateRatePlanRequest
import com.stayops.rate.api.dto.DayOfWeekRuleRequest
import com.stayops.rate.api.dto.RatePlanResponse
import com.stayops.rate.api.dto.RatePreviewResponse
import com.stayops.rate.api.dto.UpdateRatePlanRequest
import com.stayops.rate.application.service.RatePlanApplication
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.domain.model.Member
import com.stayops.rate.domain.model.DayOfWeekRate
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
    private val ratePlanApplication: RatePlanApplication,
    private val memberAccessApplication: MemberAccessApplication
) {
    @PostMapping("/rate-plans")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRatePlan(
        @PathVariable pid: String,
        @RequestBody @Valid request: CreateRatePlanRequest,
        @AuthenticationPrincipal member: Member?
    ): RatePlanResponse {
        memberAccessApplication.requirePropertyAccess(member, pid)
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
    fun getRatePlans(
        @PathVariable pid: String,
        @AuthenticationPrincipal member: Member?
    ): List<RatePlanResponse> {
        memberAccessApplication.requirePropertyAccess(member, pid)
        return ratePlanApplication.getRatePlans(pid).map { RatePlanResponse.from(it) }
    }

    @PutMapping("/rate-plans/{id}")
    fun updateRatePlan(
        @PathVariable pid: String,
        @PathVariable id: String,
        @RequestBody @Valid request: UpdateRatePlanRequest,
        @AuthenticationPrincipal member: Member?
    ): RatePlanResponse {
        memberAccessApplication.requirePropertyAccess(member, pid)
        val dateRange = if (request.dateRangeStart != null && request.dateRangeEnd != null) {
            DateRange.of(request.dateRangeStart, request.dateRangeEnd)
        } else null
        val dayOfWeekRules = request.dayOfWeekRules?.map { it.toDomain() }
        val ratePlan = ratePlanApplication.updateRatePlan(
            propertyId = pid,
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
        @PathVariable id: String,
        @AuthenticationPrincipal member: Member?
    ): RatePlanResponse {
        memberAccessApplication.requirePropertyAccess(member, pid)
        return RatePlanResponse.from(ratePlanApplication.activateRatePlan(pid, id))
    }

    @PatchMapping("/rate-plans/{id}/deactivate")
    fun deactivateRatePlan(
        @PathVariable pid: String,
        @PathVariable id: String,
        @AuthenticationPrincipal member: Member?
    ): RatePlanResponse {
        memberAccessApplication.requirePropertyAccess(member, pid)
        return RatePlanResponse.from(ratePlanApplication.deactivateRatePlan(pid, id))
    }

    @DeleteMapping("/rate-plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRatePlan(
        @PathVariable pid: String,
        @PathVariable id: String,
        @AuthenticationPrincipal member: Member?
    ) {
        memberAccessApplication.requirePropertyAccess(member, pid)
        ratePlanApplication.deleteRatePlan(id)
    }

    @GetMapping("/rates/preview")
    fun previewRates(
        @PathVariable pid: String,
        @RequestParam roomTypeId: String,
        @RequestParam basePrice: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate,
        @RequestParam(required = false) channelCode: String?,
        @AuthenticationPrincipal member: Member?
    ): List<RatePreviewResponse> {
        memberAccessApplication.requirePropertyAccess(member, pid)
        return ratePlanApplication.previewRates(pid, roomTypeId, Money.of(basePrice), startDate, endDate, channelCode)
            .map { RatePreviewResponse.from(it) }
    }

    private fun DayOfWeekRuleRequest.toDomain() = DayOfWeekRate(
        daysOfWeek = daysOfWeek.map { DayOfWeek.valueOf(it) }.toSet(),
        price = Money.of(price)
    )
}
