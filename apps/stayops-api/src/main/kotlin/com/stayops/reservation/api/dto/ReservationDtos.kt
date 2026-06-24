package com.stayops.reservation.api.dto

import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class CheckInRequest(
    val roomId: String
)

data class CreateReservationRequest(
    val roomTypeId: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val numberOfGuests: Int,
    val guestId: String,
    val guestName: String,
    val guestPhone: String,
    val guestEmail: String? = null,
    val channelCode: String,
    val externalReservationId: String? = null
)

data class PagedReservationResponse(
    val content: List<ReservationResponse>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
    val totalPages: Int
)

data class ReservationResponse(
    val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val roomId: String?,
    val guestId: String,
    val guestName: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val nightCount: Int,
    val numberOfGuests: Int,
    val status: ReservationStatus,
    val channelCode: String,
    val roomRate: BigDecimal,
    val totalAmount: BigDecimal,
    val commissionAmount: BigDecimal,
    val netAmount: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(reservation: Reservation) = ReservationResponse(
            id = reservation.id,
            propertyId = reservation.propertyId,
            roomTypeId = reservation.roomTypeId,
            roomId = reservation.roomId,
            guestId = reservation.guestId,
            guestName = reservation.guestInfo.name,
            checkIn = reservation.dateRange.checkIn,
            checkOut = reservation.dateRange.checkOut,
            nightCount = reservation.nightCount,
            numberOfGuests = reservation.numberOfGuests,
            status = reservation.status,
            channelCode = reservation.channel.channelCode,
            roomRate = reservation.pricing.roomRate.amount,
            totalAmount = reservation.pricing.totalAmount.amount,
            commissionAmount = reservation.pricing.commissionAmount.amount,
            netAmount = reservation.pricing.netAmount.amount,
            createdAt = reservation.createdAt,
            updatedAt = reservation.updatedAt
        )
    }
}
