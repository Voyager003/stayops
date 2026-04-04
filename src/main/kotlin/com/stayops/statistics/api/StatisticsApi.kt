package com.stayops.statistics.api

import com.stayops.shared.security.PropertyAccessChecker
import com.stayops.statistics.api.dto.MonthlyStatisticsResponse
import com.stayops.statistics.application.service.StatisticsApplication
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/statistics")
class StatisticsApi(
    private val statisticsApplication: StatisticsApplication,
    private val propertyAccessChecker: PropertyAccessChecker
) {

    @GetMapping("/monthly")
    fun getMonthlyStatistics(
        @PathVariable propertyId: String,
        @RequestParam year: Int,
        @RequestParam month: Int
    ): ResponseEntity<MonthlyStatisticsResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val stats = statisticsApplication.getMonthlyStatistics(propertyId, year, month)
        return ResponseEntity.ok(MonthlyStatisticsResponse.from(stats))
    }

    @GetMapping("/annual")
    fun getAnnualStatistics(
        @PathVariable propertyId: String,
        @RequestParam year: Int
    ): ResponseEntity<List<MonthlyStatisticsResponse>> {
        propertyAccessChecker.requireAccess(propertyId)
        val stats = statisticsApplication.getAnnualStatistics(propertyId, year)
        return ResponseEntity.ok(stats.map { MonthlyStatisticsResponse.from(it) })
    }
}
