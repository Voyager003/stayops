package com.stayops.reservation.api.customer

import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.domain.model.Member
import com.stayops.reservation.api.customer.dto.CustomerReservationIntentResponse
import com.stayops.reservation.api.customer.dto.PaymentConfirmRequest
import com.stayops.reservation.application.service.CustomerReservationApplication
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
@RequestMapping("/api/v1/customer/reservation-intents")
class CustomerReservationIntentApi(
    private val customerReservationApplication: CustomerReservationApplication,
    private val memberAccessApplication: MemberAccessApplication
) {

    @PostMapping("/{reservationIntentId}/confirm-payment")
    fun confirmPayment(
        @PathVariable reservationIntentId: String,
        @Valid @RequestBody request: PaymentConfirmRequest,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<CustomerReservationIntentResponse> {
        val customer = memberAccessApplication.requireCustomer(member)
        val result = customerReservationApplication.confirmReservationIntentPayment(
            memberId = customer.id,
            reservationIntentId = reservationIntentId,
            paymentKey = request.paymentKey,
            orderId = request.orderId,
            amount = request.amount
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CustomerReservationIntentResponse.from(result))
    }
}
