package com.stayops.dashboard.application.service

import com.stayops.dashboard.api.dto.DashboardResponse
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.RoomStatus
import com.stayops.room.domain.repository.RoomRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class DashboardApplication(
    private val reservationRepository: ReservationRepository,
    private val roomRepository: RoomRepository
) {

    private val revenueExcludedStatuses = setOf(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW)

    fun getDashboard(propertyId: String, today: LocalDate): DashboardResponse {
        val yesterday = today.minusDays(1)

        val todayReservations = reservationRepository.findByPropertyIdAndDateRange(propertyId, today, today)
        val yesterdayReservations = reservationRepository.findByPropertyIdAndDateRange(propertyId, yesterday, yesterday)

        val todayCheckInCount = todayReservations.count { it.dateRange.checkIn == today }
        val todayCheckOutCount = todayReservations.count { it.dateRange.checkOut == today }
        val todayRevenue = todayReservations
            .filter { it.dateRange.checkIn == today && it.status !in revenueExcludedStatuses }
            .sumOf { it.pricing.totalAmount.amount.toLong() }

        val yesterdayCheckInCount = yesterdayReservations.count { it.dateRange.checkIn == yesterday }
        val yesterdayCheckOutCount = yesterdayReservations.count { it.dateRange.checkOut == yesterday }
        val yesterdayRevenue = yesterdayReservations
            .filter { it.dateRange.checkIn == yesterday && it.status !in revenueExcludedStatuses }
            .sumOf { it.pricing.totalAmount.amount.toLong() }

        val todayNewReservations = reservationRepository.countByPropertyIdAndCreatedDate(propertyId, today)
        val yesterdayNewReservations = reservationRepository.countByPropertyIdAndCreatedDate(propertyId, yesterday)

        val pendingCount = reservationRepository
            .findByPropertyIdAndStatus(propertyId, ReservationStatus.PENDING).size

        val rooms = roomRepository.findByPropertyId(propertyId)
        val total = rooms.size
        val occupied = rooms.count { it.status == RoomStatus.OCCUPIED }
        val available = rooms.count { it.status == RoomStatus.AVAILABLE }
        val rate = if (total > 0) occupied.toDouble() / total * 100 else 0.0

        return DashboardResponse(
            todayCheckInCount = todayCheckInCount,
            todayCheckOutCount = todayCheckOutCount,
            todayRevenue = todayRevenue,
            todayNewReservations = todayNewReservations,
            yesterdayCheckInCount = yesterdayCheckInCount,
            yesterdayCheckOutCount = yesterdayCheckOutCount,
            yesterdayRevenue = yesterdayRevenue,
            yesterdayNewReservations = yesterdayNewReservations,
            pendingReservations = pendingCount,
            occupancy = DashboardResponse.OccupancyResponse(
                total = total,
                occupied = occupied,
                available = available,
                rate = Math.round(rate * 10) / 10.0
            )
        )
    }
}
