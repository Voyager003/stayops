package com.stayops.dashboard.application.dto

/**
 * 대시보드 조회 결과를 표현하는 값 객체.
 *
 * Dashboard는 도메인 모델이 없는 read-only view model이므로 별도 API 응답 DTO를
 * 두지 않고 이 타입을 Controller가 그대로 응답한다. 향후 API와 Application 간
 * 표현이 분화될 필요가 생기면 api/dto에 별도 Response 타입을 도입한다.
 */
data class DashboardSummary(
    val todayCheckInCount: Int,
    val todayCheckOutCount: Int,
    val todayRevenue: Long,
    val todayNewReservations: Int,
    val yesterdayCheckInCount: Int,
    val yesterdayCheckOutCount: Int,
    val yesterdayRevenue: Long,
    val yesterdayNewReservations: Int,
    val pendingReservations: Int,
    val occupancy: OccupancySummary
) {
    data class OccupancySummary(
        val total: Int,
        val occupied: Int,
        val available: Int,
        val rate: Double
    )
}
