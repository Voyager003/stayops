package com.stayops.statistics.api

import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.domain.model.Member
import com.stayops.statistics.api.dto.MonthlyStatisticsResponse
import com.stayops.statistics.application.service.StatisticsApplication
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/statistics")
class StatisticsApi(
    private val statisticsApplication: StatisticsApplication,
    private val memberAccessApplication: MemberAccessApplication
) {

    @GetMapping("/monthly")
    fun getMonthlyStatistics(
        @PathVariable propertyId: String,
        @RequestParam year: Int,
        @RequestParam month: Int,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<MonthlyStatisticsResponse> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val stats = statisticsApplication.getMonthlyStatistics(propertyId, year, month)
        return ResponseEntity.ok(MonthlyStatisticsResponse.from(stats))
    }

    @GetMapping("/annual")
    fun getAnnualStatistics(
        @PathVariable propertyId: String,
        @RequestParam year: Int,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<List<MonthlyStatisticsResponse>> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val stats = statisticsApplication.getAnnualStatistics(propertyId, year)
        return ResponseEntity.ok(stats.map { MonthlyStatisticsResponse.from(it) })
    }
}
