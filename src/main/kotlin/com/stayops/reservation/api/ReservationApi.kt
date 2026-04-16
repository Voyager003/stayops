package com.stayops.reservation.api

import com.stayops.reservation.api.dto.CheckInRequest
import com.stayops.reservation.api.dto.CreateReservationRequest
import com.stayops.reservation.api.dto.PagedReservationResponse
import com.stayops.reservation.api.dto.ReservationResponse
import com.stayops.reservation.application.service.ReservationApplication
import com.stayops.reservation.domain.model.DateType
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.member.infrastructure.security.PropertyAccessChecker
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

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
        @RequestParam(required = false) status: List<ReservationStatus>?,
        @RequestParam(required = false) roomTypeId: String?,
        @RequestParam(required = false) channelCode: List<String>?,
        @RequestParam(required = false) dateType: DateType?,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
        @RequestParam(required = false) guestName: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<PagedReservationResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val criteria = ReservationSearchCriteria(
            statuses = status,
            roomTypeId = roomTypeId,
            channelCodes = channelCode,
            dateType = dateType,
            startDate = startDate,
            endDate = endDate,
            guestName = guestName
        )
        val result = reservationApplication.searchReservations(propertyId, criteria, page, size)
        return ResponseEntity.ok(PagedReservationResponse(
            content = result.content.map { ReservationResponse.from(it) },
            totalElements = result.totalElements,
            page = result.page,
            size = result.size,
            totalPages = result.totalPages
        ))
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
