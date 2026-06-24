package com.stayops.reservation.application.service

import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.PagedResult
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class ReservationQueryApplication(
    private val reservationRepository: ReservationRepository
) {
    fun getReservation(propertyId: String, reservationId: String): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        require(reservation.propertyId == propertyId) { "예약이 해당 숙소에 속하지 않습니다." }
        return reservation
    }

    fun getReservations(propertyId: String): List<Reservation> =
        reservationRepository.findByPropertyId(propertyId)

    fun getReservationsByStatus(propertyId: String, status: ReservationStatus): List<Reservation> =
        reservationRepository.findByPropertyIdAndStatus(propertyId, status)

    fun searchReservations(
        propertyId: String,
        criteria: ReservationSearchCriteria,
        page: Int,
        size: Int
    ): PagedResult<Reservation> = reservationRepository.search(propertyId, criteria, page, size)

    fun searchReservationsByPropertyIds(
        propertyIds: List<String>,
        criteria: ReservationSearchCriteria,
        page: Int,
        size: Int
    ): PagedResult<Reservation> = reservationRepository.searchByPropertyIds(propertyIds, criteria, page, size)
}
