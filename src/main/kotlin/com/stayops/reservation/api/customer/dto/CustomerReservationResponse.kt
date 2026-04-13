package com.stayops.reservation.api.customer.dto

import com.stayops.reservation.application.service.CustomerReservationResult
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationStatus
import java.math.BigDecimal
import java.time.LocalDate

data class CustomerReservationResponse(
    val reservationId: String,
    val reservationStatus: ReservationStatus,
    val orderId: String,
    val amount: BigDecimal,
    val paymentStatus: PaymentStatus,
    val checkIn: LocalDate,
    val checkOut: LocalDate
) {
    companion object {
        fun from(result: CustomerReservationResult) = CustomerReservationResponse(
            reservationId = result.reservation.id,
            reservationStatus = result.reservation.status,
            orderId = result.payment.orderId,
            amount = result.payment.amount.amount,
            paymentStatus = result.payment.status,
            checkIn = result.reservation.dateRange.checkIn,
            checkOut = result.reservation.dateRange.checkOut
        )
    }
}

data class MyReservationResponse(
    val reservationId: String,
    val propertyId: String,
    val roomTypeId: String,
    val status: ReservationStatus,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val nightCount: Int,
    val numberOfGuests: Int,
    val guestName: String,
    val totalAmount: BigDecimal
) {
    companion object {
        fun from(reservation: Reservation) = MyReservationResponse(
            reservationId = reservation.id,
            propertyId = reservation.propertyId,
            roomTypeId = reservation.roomTypeId,
            status = reservation.status,
            checkIn = reservation.dateRange.checkIn,
            checkOut = reservation.dateRange.checkOut,
            nightCount = reservation.nightCount,
            numberOfGuests = reservation.numberOfGuests,
            guestName = reservation.guestInfo.name,
            totalAmount = reservation.pricing.totalAmount.amount
        )
    }
}
