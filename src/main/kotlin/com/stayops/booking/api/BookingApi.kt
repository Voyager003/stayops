package com.stayops.booking.api

import com.stayops.booking.api.dto.BookingResponse
import com.stayops.booking.api.dto.CreateBookingRequest
import com.stayops.booking.api.dto.PaymentConfirmRequest
import com.stayops.booking.application.service.BookingApplication
import com.stayops.member.infrastructure.security.CustomerAuthChecker
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/booking/reservations")
class BookingApi(
    private val bookingApplication: BookingApplication,
    private val customerAuthChecker: CustomerAuthChecker
) {

    @PostMapping
    fun createBooking(@Valid @RequestBody request: CreateBookingRequest): ResponseEntity<BookingResponse> {
        val member = customerAuthChecker.requireCustomer()
        val result = bookingApplication.createBooking(
            memberId = member.id,
            propertyId = request.propertyId,
            roomTypeId = request.roomTypeId,
            checkIn = request.checkIn,
            checkOut = request.checkOut,
            numberOfGuests = request.numberOfGuests,
            guestName = request.guestName,
            guestPhone = request.guestPhone,
            guestEmail = request.guestEmail
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(result))
    }

    @PostMapping("/{reservationId}/confirm-payment")
    fun confirmPayment(
        @PathVariable reservationId: String,
        @Valid @RequestBody request: PaymentConfirmRequest
    ): ResponseEntity<BookingResponse> {
        val member = customerAuthChecker.requireCustomer()
        val result = bookingApplication.confirmPayment(
            memberId = member.id,
            reservationId = reservationId,
            paymentKey = request.paymentKey,
            orderId = request.orderId,
            amount = request.amount
        )
        return ResponseEntity.ok(BookingResponse.from(result))
    }
}
