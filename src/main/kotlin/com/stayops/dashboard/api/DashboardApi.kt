package com.stayops.dashboard.api

import com.stayops.dashboard.api.dto.DashboardResponse
import com.stayops.dashboard.application.service.DashboardApplication
import com.stayops.shared.security.PropertyAccessChecker
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/dashboard")
class DashboardApi(
    private val dashboardApplication: DashboardApplication,
    private val propertyAccessChecker: PropertyAccessChecker
) {

    @GetMapping
    fun getDashboard(@PathVariable propertyId: String): ResponseEntity<DashboardResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val dashboard = dashboardApplication.getDashboard(propertyId, LocalDate.now())
        return ResponseEntity.ok(dashboard)
    }
}
