package com.stayops.reservation.api.customer

import com.stayops.reservation.api.customer.dto.CustomerReservationResponse
import com.stayops.reservation.api.customer.dto.CreateCustomerReservationRequest
import com.stayops.reservation.api.customer.dto.PaymentConfirmRequest
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.member.infrastructure.security.CustomerAuthChecker
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/customer/reservations")
class CustomerReservationApi(
    private val customerReservationApplication: CustomerReservationApplication,
    private val customerAuthChecker: CustomerAuthChecker
) {

    @PostMapping
    fun createReservation(@Valid @RequestBody request: CreateCustomerReservationRequest): ResponseEntity<CustomerReservationResponse> {
        val member = customerAuthChecker.requireCustomer()
        val result = customerReservationApplication.createReservation(
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
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerReservationResponse.from(result))
    }

    @PostMapping("/{reservationId}/confirm-payment")
    fun confirmPayment(
        @PathVariable reservationId: String,
        @Valid @RequestBody request: PaymentConfirmRequest
    ): ResponseEntity<CustomerReservationResponse> {
        val member = customerAuthChecker.requireCustomer()
        val result = customerReservationApplication.confirmPayment(
            memberId = member.id,
            reservationId = reservationId,
            paymentKey = request.paymentKey,
            orderId = request.orderId,
            amount = request.amount
        )
        return ResponseEntity.ok(CustomerReservationResponse.from(result))
    }
}
