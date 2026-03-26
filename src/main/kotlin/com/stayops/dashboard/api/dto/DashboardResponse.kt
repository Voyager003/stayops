package com.stayops.dashboard.api.dto

data class DashboardResponse(
    val todayCheckInCount: Int,
    val todayCheckOutCount: Int,
    val pendingReservations: Int,
    val occupancy: OccupancyResponse
) {
    data class OccupancyResponse(
        val total: Int,
        val occupied: Int,
        val available: Int,
        val rate: Double
    )
}
