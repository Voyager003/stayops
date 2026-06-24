package com.stayops.reservation.api.customer

import com.stayops.reservation.api.customer.dto.CustomerReservationResponse
import com.stayops.reservation.api.customer.dto.MyReservationResponse
import com.stayops.reservation.api.customer.dto.PagedMyReservationResponse
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.domain.model.Member
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/customer/reservations")
class CustomerReservationMyPageApi(
    private val customerReservationApplication: CustomerReservationApplication,
    private val memberAccessApplication: MemberAccessApplication
) {

    @GetMapping
    fun getMyReservations(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<PagedMyReservationResponse> {
        val customer = memberAccessApplication.requireCustomer(member)
        val reservations = customerReservationApplication.getMyReservations(customer.id, page, size)
        return ResponseEntity.ok(
            PagedMyReservationResponse(
                content = reservations.content.map { MyReservationResponse.from(it) },
                totalElements = reservations.totalElements,
                page = reservations.page,
                size = reservations.size,
                totalPages = reservations.totalPages
            )
        )
    }

    @GetMapping("/{reservationId}")
    fun getMyReservation(
        @PathVariable reservationId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<MyReservationResponse> {
        val customer = memberAccessApplication.requireCustomer(member)
        val reservation = customerReservationApplication.getMyReservation(customer.id, reservationId)
        return ResponseEntity.ok(MyReservationResponse.from(reservation))
    }

    @PostMapping("/{reservationId}/cancel")
    fun cancelReservation(
        @PathVariable reservationId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<CustomerReservationResponse> {
        val customer = memberAccessApplication.requireCustomer(member)
        val result = customerReservationApplication.cancelReservation(customer.id, reservationId)
        return ResponseEntity.ok(CustomerReservationResponse.from(result))
    }
}
