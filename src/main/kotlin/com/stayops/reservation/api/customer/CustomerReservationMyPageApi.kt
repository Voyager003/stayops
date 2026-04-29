package com.stayops.reservation.api.customer

import com.stayops.reservation.api.customer.dto.CustomerReservationResponse
import com.stayops.reservation.api.customer.dto.MyReservationResponse
import com.stayops.reservation.api.customer.dto.PagedMyReservationResponse
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.member.infrastructure.security.CustomerAuthChecker
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/customer/reservations")
class CustomerReservationMyPageApi(
    private val customerReservationApplication: CustomerReservationApplication,
    private val customerAuthChecker: CustomerAuthChecker
) {

    @GetMapping
    fun getMyReservations(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedMyReservationResponse> {
        val member = customerAuthChecker.requireCustomer()
        val reservations = customerReservationApplication.getMyReservations(member.id, page, size)
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
    fun getMyReservation(@PathVariable reservationId: String): ResponseEntity<MyReservationResponse> {
        val member = customerAuthChecker.requireCustomer()
        val reservation = customerReservationApplication.getMyReservation(member.id, reservationId)
        return ResponseEntity.ok(MyReservationResponse.from(reservation))
    }

    @PostMapping("/{reservationId}/cancel")
    fun cancelReservation(@PathVariable reservationId: String): ResponseEntity<CustomerReservationResponse> {
        val member = customerAuthChecker.requireCustomer()
        val result = customerReservationApplication.cancelReservation(member.id, reservationId)
        return ResponseEntity.ok(CustomerReservationResponse.from(result))
    }
}
