package com.stayops.reservation.api.customer

import com.stayops.reservation.api.customer.dto.BookingDraftResponse
import com.stayops.reservation.api.customer.dto.CreateBookingDraftRequest
import com.stayops.reservation.application.service.CustomerBookingDraftApplication
import com.stayops.shared.exception.NotFoundException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/customer/booking-drafts")
class CustomerBookingDraftApi(
    private val bookingDraftApplication: CustomerBookingDraftApplication
) {

    @PostMapping
    fun createDraft(@Valid @RequestBody request: CreateBookingDraftRequest): ResponseEntity<BookingDraftResponse> {
        val draft = bookingDraftApplication.createDraft(
            propertyId = request.propertyId,
            roomTypeId = request.roomTypeId,
            checkIn = request.checkIn,
            checkOut = request.checkOut,
            guests = request.guests
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingDraftResponse.from(draft))
    }

    @GetMapping("/{draftId}")
    fun getDraft(@PathVariable draftId: String): ResponseEntity<BookingDraftResponse> {
        val draft = bookingDraftApplication.getDraft(draftId)
            ?: throw NotFoundException("BOOKING_DRAFT_NOT_FOUND", "예약 진행 정보를 찾을 수 없습니다: $draftId")
        return ResponseEntity.ok(BookingDraftResponse.from(draft))
    }

    @DeleteMapping("/{draftId}")
    fun deleteDraft(@PathVariable draftId: String): ResponseEntity<Void> {
        bookingDraftApplication.deleteDraft(draftId)
        return ResponseEntity.noContent().build()
    }
}
