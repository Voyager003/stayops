package com.stayops.reservation.api

import com.stayops.reservation.api.dto.CheckInRequest
import com.stayops.reservation.api.dto.CreateReservationRequest
import com.stayops.reservation.api.dto.ReservationResponse
import com.stayops.reservation.application.service.ReservationApplication
import com.stayops.shared.security.PropertyAccessChecker
import com.stayops.reservation.domain.model.ReservationStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/reservations")
class ReservationApi(
    private val reservationApplication: ReservationApplication,
    private val propertyAccessChecker: PropertyAccessChecker
) {

    @PostMapping
    fun createReservation(
        @PathVariable propertyId: String,
        @RequestBody request: CreateReservationRequest
    ): ResponseEntity<ReservationResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val reservation = reservationApplication.createReservation(
            propertyId = propertyId,
            roomTypeId = request.roomTypeId,
            checkIn = request.checkIn,
            checkOut = request.checkOut,
            numberOfGuests = request.numberOfGuests,
            guestId = request.guestId,
            guestName = request.guestName,
            guestPhone = request.guestPhone,
            guestEmail = request.guestEmail,
            channelCode = request.channelCode,
            externalReservationId = request.externalReservationId
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.from(reservation))
    }

    @GetMapping
    fun getReservations(
        @PathVariable propertyId: String,
        @RequestParam(required = false) status: ReservationStatus?
    ): ResponseEntity<List<ReservationResponse>> {
        propertyAccessChecker.requireAccess(propertyId)
        val reservations = if (status != null) {
            reservationApplication.getReservationsByStatus(propertyId, status)
        } else {
            reservationApplication.getReservations(propertyId)
        }
        return ResponseEntity.ok(reservations.map { ReservationResponse.from(it) })
    }

    @GetMapping("/{reservationId}")
    fun getReservation(
        @PathVariable propertyId: String,
        @PathVariable reservationId: String
    ): ResponseEntity<ReservationResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val reservation = reservationApplication.getReservation(propertyId, reservationId)
        return ResponseEntity.ok(ReservationResponse.from(reservation))
    }

    @PostMapping("/{reservationId}/confirm")
    fun confirmReservation(
        @PathVariable propertyId: String,
        @PathVariable reservationId: String
    ): ResponseEntity<ReservationResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val reservation = reservationApplication.confirmReservation(propertyId, reservationId)
        return ResponseEntity.ok(ReservationResponse.from(reservation))
    }

    @PostMapping("/{reservationId}/cancel")
    fun cancelReservation(
        @PathVariable propertyId: String,
        @PathVariable reservationId: String
    ): ResponseEntity<ReservationResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val reservation = reservationApplication.cancelReservation(propertyId, reservationId)
        return ResponseEntity.ok(ReservationResponse.from(reservation))
    }

    @PostMapping("/{reservationId}/check-in")
    fun checkInReservation(
        @PathVariable propertyId: String,
        @PathVariable reservationId: String,
        @RequestBody request: CheckInRequest
    ): ResponseEntity<ReservationResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val reservation = reservationApplication.checkInReservation(propertyId, reservationId, request.roomId)
        return ResponseEntity.ok(ReservationResponse.from(reservation))
    }

    @PostMapping("/{reservationId}/check-out")
    fun checkOutReservation(
        @PathVariable propertyId: String,
        @PathVariable reservationId: String
    ): ResponseEntity<ReservationResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val reservation = reservationApplication.checkOutReservation(propertyId, reservationId)
        return ResponseEntity.ok(ReservationResponse.from(reservation))
    }

    @PostMapping("/{reservationId}/no-show")
    fun noShowReservation(
        @PathVariable propertyId: String,
        @PathVariable reservationId: String
    ): ResponseEntity<ReservationResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val reservation = reservationApplication.noShowReservation(propertyId, reservationId)
        return ResponseEntity.ok(ReservationResponse.from(reservation))
    }
}
