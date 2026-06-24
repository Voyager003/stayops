package com.stayops.settlement.api

import com.stayops.member.domain.model.Member
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.settlement.api.dto.DailySettlementResponse
import com.stayops.settlement.api.dto.MonthlySettlementResponse
import com.stayops.settlement.api.dto.SettlementResponse
import com.stayops.settlement.application.service.SettlementQueryApplication
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth

@RestController
class SettlementApi(
    private val settlementQueryApplication: SettlementQueryApplication,
    private val memberAccessApplication: MemberAccessApplication
) {

    @GetMapping("/api/v1/properties/{propertyId}/settlements")
    fun getSettlement(
        @PathVariable propertyId: String,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<SettlementResponse> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val (resolvedStart, resolvedEnd) = resolveDateRange(year, month, startDate, endDate)
        val summary = settlementQueryApplication.getSettlementSummary(propertyId, resolvedStart, resolvedEnd)
        return ResponseEntity.ok(SettlementResponse.from(summary))
    }

    @GetMapping("/api/v1/properties/{propertyId}/settlements/daily-trend")
    fun getDailyTrend(
        @PathVariable propertyId: String,
        @RequestParam startDate: LocalDate,
        @RequestParam endDate: LocalDate,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<List<DailySettlementResponse>> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val trend = settlementQueryApplication.getDailyTrend(propertyId, startDate, endDate)
        return ResponseEntity.ok(trend.map { DailySettlementResponse.from(it) })
    }

    @GetMapping("/api/v1/properties/{propertyId}/settlements/monthly-trend")
    fun getMonthlyTrend(
        @PathVariable propertyId: String,
        @RequestParam year: Int,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<List<MonthlySettlementResponse>> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val trend = settlementQueryApplication.getMonthlyTrend(propertyId, year)
        return ResponseEntity.ok(trend.map { MonthlySettlementResponse.from(it) })
    }

    @GetMapping("/api/v1/settlements")
    fun getAllPropertiesSettlement(
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<SettlementResponse> {
        val propertyIds = memberAccessApplication.resolveAccessiblePropertyIds(member)

        val (resolvedStart, resolvedEnd) = resolveDateRange(year, month, startDate, endDate)
        val summary = settlementQueryApplication.getSettlementSummaryByPropertyIds(propertyIds, resolvedStart, resolvedEnd)
        return ResponseEntity.ok(SettlementResponse.from(summary))
    }

    private fun resolveDateRange(
        year: Int?,
        month: Int?,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): Pair<LocalDate, LocalDate> {
        return if (year != null && month != null) {
            val ym = YearMonth.of(year, month)
            Pair(ym.atDay(1), ym.atEndOfMonth())
        } else {
            requireNotNull(startDate) { "startDate 또는 year+month 파라미터가 필요합니다." }
            requireNotNull(endDate) { "endDate 또는 year+month 파라미터가 필요합니다." }
            Pair(startDate, endDate)
        }
    }
}
