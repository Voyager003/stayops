package com.stayops.reservation.api.customer

import com.stayops.reservation.api.customer.dto.CustomerReservationResponse
import com.stayops.reservation.api.customer.dto.CustomerReservationIntentResponse
import com.stayops.reservation.api.customer.dto.CreateCustomerReservationRequest
import com.stayops.reservation.api.customer.dto.PaymentConfirmRequest
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.domain.model.Member
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/customer/reservations")
class CustomerReservationApi(
    private val customerReservationApplication: CustomerReservationApplication,
    private val memberAccessApplication: MemberAccessApplication
) {

    @PostMapping
    fun createReservation(
        @Valid @RequestBody request: CreateCustomerReservationRequest,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<CustomerReservationIntentResponse> {
        val customer = memberAccessApplication.requireCustomer(member)
        val result = customerReservationApplication.createReservationIntent(
            memberId = customer.id,
            propertyId = request.propertyId,
            roomTypeId = request.roomTypeId,
            checkIn = request.checkIn,
            checkOut = request.checkOut,
            numberOfGuests = request.numberOfGuests,
            guestName = request.guestName,
            guestPhone = request.guestPhone,
            guestEmail = request.guestEmail
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerReservationIntentResponse.from(result))
    }

    @PostMapping("/{reservationId}/confirm-payment")
    fun confirmPayment(
        @PathVariable reservationId: String,
        @Valid @RequestBody request: PaymentConfirmRequest,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<CustomerReservationResponse> {
        val customer = memberAccessApplication.requireCustomer(member)
        val result = customerReservationApplication.confirmPayment(
            memberId = customer.id,
            reservationId = reservationId,
            paymentKey = request.paymentKey,
            orderId = request.orderId,
            amount = request.amount
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CustomerReservationResponse.from(result))
    }
}
