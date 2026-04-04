package com.stayops.settlement.api

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.settlement.api.dto.DailySettlementResponse
import com.stayops.settlement.api.dto.MonthlySettlementResponse
import com.stayops.settlement.api.dto.SettlementResponse
import com.stayops.settlement.application.service.SettlementQueryService
import com.stayops.shared.security.PropertyAccessChecker
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.YearMonth

@RestController
class SettlementApi(
    private val settlementQueryService: SettlementQueryService,
    private val propertyAccessChecker: PropertyAccessChecker,
    private val propertyRepository: PropertyRepository
) {

    @GetMapping("/api/v1/properties/{propertyId}/settlements")
    fun getSettlement(
        @PathVariable propertyId: String,
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?
    ): ResponseEntity<SettlementResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val (resolvedStart, resolvedEnd) = resolveDateRange(year, month, startDate, endDate)
        val summary = settlementQueryService.getSettlementSummary(propertyId, resolvedStart, resolvedEnd)
        return ResponseEntity.ok(SettlementResponse.from(summary))
    }

    @GetMapping("/api/v1/properties/{propertyId}/settlements/daily-trend")
    fun getDailyTrend(
        @PathVariable propertyId: String,
        @RequestParam startDate: LocalDate,
        @RequestParam endDate: LocalDate
    ): ResponseEntity<List<DailySettlementResponse>> {
        propertyAccessChecker.requireAccess(propertyId)
        val trend = settlementQueryService.getDailyTrend(propertyId, startDate, endDate)
        return ResponseEntity.ok(trend.map { DailySettlementResponse.from(it) })
    }

    @GetMapping("/api/v1/properties/{propertyId}/settlements/monthly-trend")
    fun getMonthlyTrend(
        @PathVariable propertyId: String,
        @RequestParam year: Int
    ): ResponseEntity<List<MonthlySettlementResponse>> {
        propertyAccessChecker.requireAccess(propertyId)
        val trend = settlementQueryService.getMonthlyTrend(propertyId, year)
        return ResponseEntity.ok(trend.map { MonthlySettlementResponse.from(it) })
    }

    @GetMapping("/api/v1/settlements")
    fun getAllPropertiesSettlement(
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?
    ): ResponseEntity<SettlementResponse> {
        val member = SecurityContextHolder.getContext().authentication?.principal as Member
        val propertyIds = if (member.role == MemberRole.ADMIN) {
            propertyRepository.findAll().map { it.id }
        } else {
            member.propertyAccess.map { it.propertyId }
        }

        val (resolvedStart, resolvedEnd) = resolveDateRange(year, month, startDate, endDate)
        val summary = settlementQueryService.getSettlementSummaryByPropertyIds(propertyIds, resolvedStart, resolvedEnd)
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
