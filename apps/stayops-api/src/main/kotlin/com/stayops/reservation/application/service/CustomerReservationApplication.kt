package com.stayops.reservation.application.service

import com.stayops.reservation.application.port.ReservationPaymentSnapshot
import com.stayops.reservation.domain.model.Reservation
import com.stayops.shared.domain.PagedResult
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class CustomerReservationApplication(
    private val creationApplication: CustomerReservationCreationApplication,
    private val queryApplication: CustomerReservationQueryApplication,
    private val paymentApplication: CustomerReservationPaymentApplication,
    private val cancellationApplication: CustomerReservationCancellationApplication
) {

    fun createReservation(
        memberId: String,
        propertyId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        numberOfGuests: Int,
        guestName: String,
        guestPhone: String,
        guestEmail: String?
    ): CustomerReservationResult =
        creationApplication.createReservation(
            memberId = memberId,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            checkIn = checkIn,
            checkOut = checkOut,
            numberOfGuests = numberOfGuests,
            guestName = guestName,
            guestPhone = guestPhone,
            guestEmail = guestEmail
        )

    fun getMyReservations(memberId: String, page: Int = 0, size: Int = 20): PagedResult<CustomerReservationReadResult> =
        queryApplication.getMyReservations(memberId, page, size)

    fun getMyReservation(memberId: String, reservationId: String): CustomerReservationReadResult =
        queryApplication.getMyReservation(memberId, reservationId)

    fun confirmPayment(
        memberId: String,
        reservationId: String,
        paymentKey: String,
        orderId: String,
        amount: BigDecimal
    ): CustomerReservationResult =
        paymentApplication.confirmPayment(
            memberId = memberId,
            reservationId = reservationId,
            paymentKey = paymentKey,
            orderId = orderId,
            amount = amount
        )

    fun cancelReservation(memberId: String, reservationId: String): CustomerReservationResult =
        cancellationApplication.cancelReservation(memberId, reservationId)
}

data class CustomerReservationResult(
    val reservation: Reservation,
    val payment: ReservationPaymentSnapshot
)

data class CustomerReservationReadResult(
    val reservation: Reservation,
    val payment: ReservationPaymentSnapshot?
)
