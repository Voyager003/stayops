package com.stayops.reservation.api.customer.dto

import com.stayops.reservation.application.service.CustomerReservationReadResult
import com.stayops.reservation.application.service.CustomerReservationResult
import com.stayops.reservation.application.port.ReservationPaymentStatus
import com.stayops.reservation.domain.model.ReservationStatus
import java.math.BigDecimal
import java.time.LocalDate

data class CustomerReservationResponse(
    val reservationId: String,
    val reservationStatus: ReservationStatus,
    val orderId: String,
    val amount: BigDecimal,
    val paymentStatus: ReservationPaymentStatus,
    val confirmationStatus: ReservationConfirmationStatus,
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
            confirmationStatus = ReservationConfirmationStatus.from(
                result.reservation.status,
                result.payment.status
            ),
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
    val paymentStatus: ReservationPaymentStatus?,
    val confirmationStatus: ReservationConfirmationStatus,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val nightCount: Int,
    val numberOfGuests: Int,
    val guestName: String,
    val totalAmount: BigDecimal
) {
    companion object {
        fun from(result: CustomerReservationReadResult) = MyReservationResponse(
            reservationId = result.reservation.id,
            propertyId = result.reservation.propertyId,
            roomTypeId = result.reservation.roomTypeId,
            status = result.reservation.status,
            paymentStatus = result.payment?.status,
            confirmationStatus = ReservationConfirmationStatus.from(
                result.reservation.status,
                result.payment?.status
            ),
            checkIn = result.reservation.dateRange.checkIn,
            checkOut = result.reservation.dateRange.checkOut,
            nightCount = result.reservation.nightCount,
            numberOfGuests = result.reservation.numberOfGuests,
            guestName = result.reservation.guestInfo.name,
            totalAmount = result.reservation.pricing.totalAmount.amount
        )
    }
}
