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

    fun getDashboard(propertyId: String, today: LocalDate): DashboardResponse {
        val todayReservations = reservationRepository.findByPropertyIdAndDateRange(propertyId, today, today)

        val checkInCount = todayReservations.count { it.dateRange.checkIn == today }
        val checkOutCount = todayReservations.count { it.dateRange.checkOut == today }

        val pendingCount = reservationRepository
            .findByPropertyIdAndStatus(propertyId, ReservationStatus.PENDING).size

        val rooms = roomRepository.findByPropertyId(propertyId)
        val total = rooms.size
        val occupied = rooms.count { it.status == RoomStatus.OCCUPIED }
        val available = rooms.count { it.status == RoomStatus.AVAILABLE }
        val rate = if (total > 0) occupied.toDouble() / total * 100 else 0.0

        return DashboardResponse(
            todayCheckInCount = checkInCount,
            todayCheckOutCount = checkOutCount,
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
