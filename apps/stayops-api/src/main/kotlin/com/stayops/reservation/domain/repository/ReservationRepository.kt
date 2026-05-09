package com.stayops.reservation.domain.repository

import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.shared.domain.PagedResult
import java.time.Instant
import java.time.LocalDate

interface ReservationRepository {
    fun save(reservation: Reservation): Reservation
    fun findById(id: String): Reservation?
    fun findByPropertyId(propertyId: String): List<Reservation>
    fun findByPropertyIdAndStatus(propertyId: String, status: ReservationStatus): List<Reservation>
    fun findByPropertyIdAndDateRange(propertyId: String, startDate: LocalDate, endDate: LocalDate): List<Reservation>
    fun findByPropertyIdAndGuestId(propertyId: String, guestId: String): List<Reservation>
    fun findByPropertyIdAndChannelCode(propertyId: String, channelCode: String): List<Reservation>
    fun findByMemberId(memberId: String): List<Reservation>
    fun findPageByMemberId(memberId: String, page: Int, size: Int): PagedResult<Reservation>
    fun search(propertyId: String, criteria: ReservationSearchCriteria, page: Int, size: Int): PagedResult<Reservation>
    fun searchByPropertyIds(propertyIds: List<String>, criteria: ReservationSearchCriteria, page: Int, size: Int): PagedResult<Reservation>
    fun countByPropertyIdAndCreatedDate(propertyId: String, date: LocalDate): Int
    fun existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
        memberId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        now: Instant
    ): Boolean
}
