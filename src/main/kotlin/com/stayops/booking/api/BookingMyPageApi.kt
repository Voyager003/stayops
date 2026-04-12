package com.stayops.booking.api

import com.stayops.booking.api.dto.BookingResponse
import com.stayops.booking.api.dto.MyReservationResponse
import com.stayops.booking.application.service.BookingApplication
import com.stayops.member.infrastructure.security.CustomerAuthChecker
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/booking/my/reservations")
class BookingMyPageApi(
    private val bookingApplication: BookingApplication,
    private val customerAuthChecker: CustomerAuthChecker
) {

    @GetMapping
    fun getMyReservations(): ResponseEntity<List<MyReservationResponse>> {
        val member = customerAuthChecker.requireCustomer()
        val reservations = bookingApplication.getMyReservations(member.id)
        return ResponseEntity.ok(reservations.map { MyReservationResponse.from(it) })
    }

    @GetMapping("/{reservationId}")
    fun getMyReservation(@PathVariable reservationId: String): ResponseEntity<MyReservationResponse> {
        val member = customerAuthChecker.requireCustomer()
        val reservation = bookingApplication.getMyReservation(member.id, reservationId)
        return ResponseEntity.ok(MyReservationResponse.from(reservation))
    }

    @PostMapping("/{reservationId}/cancel")
    fun cancelReservation(@PathVariable reservationId: String): ResponseEntity<BookingResponse> {
        val member = customerAuthChecker.requireCustomer()
        val result = bookingApplication.cancelBooking(member.id, reservationId)
        return ResponseEntity.ok(BookingResponse.from(result))
    }
}
