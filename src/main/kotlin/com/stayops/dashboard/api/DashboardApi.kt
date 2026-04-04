package com.stayops.dashboard.api

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.dashboard.api.dto.DashboardResponse
import com.stayops.dashboard.application.service.DashboardApplication
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.security.PropertyAccessChecker
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
class DashboardApi(
    private val dashboardApplication: DashboardApplication,
    private val propertyAccessChecker: PropertyAccessChecker,
    private val propertyRepository: PropertyRepository
) {

    @GetMapping("/api/v1/properties/{propertyId}/dashboard")
    fun getDashboard(@PathVariable propertyId: String): ResponseEntity<DashboardResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val dashboard = dashboardApplication.getDashboard(propertyId, LocalDate.now())
        return ResponseEntity.ok(dashboard)
    }

    @GetMapping("/api/v1/dashboard")
    fun getAllPropertiesDashboard(): ResponseEntity<DashboardResponse> {
        val member = SecurityContextHolder.getContext().authentication?.principal as Member
        val propertyIds = if (member.role == MemberRole.ADMIN) {
            propertyRepository.findAll().map { it.id }
        } else {
            member.propertyAccess.map { it.propertyId }
        }
        val dashboard = dashboardApplication.getAggregatedDashboard(propertyIds, LocalDate.now())
        return ResponseEntity.ok(dashboard)
    }
}
