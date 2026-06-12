package com.stayops.dashboard.api

import com.stayops.member.domain.model.Member
import com.stayops.dashboard.application.dto.DashboardSummary
import com.stayops.dashboard.application.service.DashboardApplication
import com.stayops.member.application.service.MemberAccessApplication
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDate

@RestController
class DashboardApi(
    private val dashboardApplication: DashboardApplication,
    private val memberAccessApplication: MemberAccessApplication,
    private val clock: Clock
) {

    @GetMapping("/api/v1/properties/{propertyId}/dashboard")
    fun getDashboard(
        @PathVariable propertyId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<DashboardSummary> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        return ResponseEntity.ok(dashboardApplication.getDashboard(propertyId, LocalDate.now(clock)))
    }

    @GetMapping("/api/v1/dashboard")
    fun getAllPropertiesDashboard(
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<DashboardSummary> {
        val propertyIds = memberAccessApplication.resolveAccessiblePropertyIds(member)
        return ResponseEntity.ok(dashboardApplication.getAggregatedDashboard(propertyIds, LocalDate.now(clock)))
    }
}
