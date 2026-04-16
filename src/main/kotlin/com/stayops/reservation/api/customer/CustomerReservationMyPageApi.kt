package com.stayops.reservation.api.customer

import com.stayops.reservation.api.customer.dto.CustomerReservationResponse
import com.stayops.reservation.api.customer.dto.MyReservationResponse
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
    fun getMyReservations(): ResponseEntity<List<MyReservationResponse>> {
        val member = customerAuthChecker.requireCustomer()
        val reservations = customerReservationApplication.getMyReservations(member.id)
        return ResponseEntity.ok(reservations.map { MyReservationResponse.from(it) })
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
