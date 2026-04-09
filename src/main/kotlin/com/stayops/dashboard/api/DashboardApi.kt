package com.stayops.dashboard.api

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.dashboard.application.dto.DashboardSummary
import com.stayops.dashboard.application.service.DashboardApplication
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.security.PropertyAccessChecker
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.Clock
import java.time.LocalDate

@RestController
class DashboardApi(
    private val dashboardApplication: DashboardApplication,
    private val propertyAccessChecker: PropertyAccessChecker,
    private val propertyRepository: PropertyRepository,
    private val clock: Clock
) {

    @GetMapping("/api/v1/properties/{propertyId}/dashboard")
    fun getDashboard(@PathVariable propertyId: String): ResponseEntity<DashboardSummary> {
        propertyAccessChecker.requireAccess(propertyId)
        return ResponseEntity.ok(dashboardApplication.getDashboard(propertyId, LocalDate.now(clock)))
    }

    @GetMapping("/api/v1/dashboard")
    fun getAllPropertiesDashboard(): ResponseEntity<DashboardSummary> {
        val member = SecurityContextHolder.getContext().authentication?.principal as Member
        val propertyIds = if (member.role == MemberRole.ADMIN) {
            propertyRepository.findAll().map { it.id }
        } else {
            member.propertyAccess.map { it.propertyId }
        }
        return ResponseEntity.ok(dashboardApplication.getAggregatedDashboard(propertyIds, LocalDate.now(clock)))
    }
}
