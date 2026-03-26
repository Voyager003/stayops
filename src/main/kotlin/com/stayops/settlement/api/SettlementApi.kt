package com.stayops.settlement.api

import com.stayops.settlement.api.dto.DailySettlementResponse
import com.stayops.settlement.api.dto.MonthlySettlementResponse
import com.stayops.settlement.api.dto.SettlementResponse
import com.stayops.settlement.application.service.SettlementQueryService
import com.stayops.shared.security.PropertyAccessChecker
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/settlements")
class SettlementApi(
    private val settlementQueryService: SettlementQueryService,
    private val propertyAccessChecker: PropertyAccessChecker
) {

    @GetMapping
    fun getSettlement(
        @PathVariable propertyId: String,
        @RequestParam startDate: LocalDate,
        @RequestParam endDate: LocalDate
    ): ResponseEntity<SettlementResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val summary = settlementQueryService.getSettlementSummary(propertyId, startDate, endDate)
        return ResponseEntity.ok(SettlementResponse.from(summary))
    }

    @GetMapping("/daily-trend")
    fun getDailyTrend(
        @PathVariable propertyId: String,
        @RequestParam startDate: LocalDate,
        @RequestParam endDate: LocalDate
    ): ResponseEntity<List<DailySettlementResponse>> {
        propertyAccessChecker.requireAccess(propertyId)
        val trend = settlementQueryService.getDailyTrend(propertyId, startDate, endDate)
        return ResponseEntity.ok(trend.map { DailySettlementResponse.from(it) })
    }

    @GetMapping("/monthly-trend")
    fun getMonthlyTrend(
        @PathVariable propertyId: String,
        @RequestParam year: Int
    ): ResponseEntity<List<MonthlySettlementResponse>> {
        propertyAccessChecker.requireAccess(propertyId)
        val trend = settlementQueryService.getMonthlyTrend(propertyId, year)
        return ResponseEntity.ok(trend.map { MonthlySettlementResponse.from(it) })
    }
}
