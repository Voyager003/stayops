package com.stayops.reservation.api.customer

import com.stayops.reservation.api.customer.dto.*
import com.stayops.reservation.application.service.ReservationSearchApplication
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/customer/properties")
class ReservationSearchApi(
    private val reservationSearchApplication: ReservationSearchApplication
) {

    @GetMapping
    fun searchProperties(): ResponseEntity<List<PropertySearchResponse>> {
        val properties = reservationSearchApplication.searchProperties()
        return ResponseEntity.ok(properties.map { PropertySearchResponse.from(it) })
    }

    @GetMapping("/{propertyId}")
    fun getProperty(@PathVariable propertyId: String): ResponseEntity<PropertySearchResponse> {
        val property = reservationSearchApplication.getProperty(propertyId)
        return ResponseEntity.ok(PropertySearchResponse.from(property))
    }

    @GetMapping("/{propertyId}/room-types")
    fun searchRoomTypes(@PathVariable propertyId: String): ResponseEntity<List<RoomTypeSearchResponse>> {
        val roomTypes = reservationSearchApplication.searchRoomTypes(propertyId)
        return ResponseEntity.ok(roomTypes.map { RoomTypeSearchResponse.from(it) })
    }

    @GetMapping("/{propertyId}/offers")
    fun getReservationOffers(
        @PathVariable propertyId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkIn: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkOut: LocalDate,
        @RequestParam guests: Int
    ): ResponseEntity<List<ReservationOfferResponse>> {
        val offers = reservationSearchApplication.getReservationOffers(propertyId, checkIn, checkOut, guests)
        return ResponseEntity.ok(offers.map { ReservationOfferResponse.from(it) })
    }

    @GetMapping("/{propertyId}/room-types/{roomTypeId}/availability")
    fun getAvailability(
        @PathVariable propertyId: String,
        @PathVariable roomTypeId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkIn: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkOut: LocalDate
    ): ResponseEntity<List<AvailabilityResponse>> {
        val inventories = reservationSearchApplication.getAvailability(propertyId, roomTypeId, checkIn, checkOut)
        return ResponseEntity.ok(inventories.map { AvailabilityResponse.from(it) })
    }

    @GetMapping("/{propertyId}/room-types/{roomTypeId}/rates")
    fun getRatePreview(
        @PathVariable propertyId: String,
        @PathVariable roomTypeId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkIn: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkOut: LocalDate
    ): ResponseEntity<RatePreviewResponse> {
        val totalAmount = reservationSearchApplication.getRatePreview(propertyId, roomTypeId, checkIn, checkOut)
        return ResponseEntity.ok(RatePreviewResponse.of(totalAmount, checkIn, checkOut))
    }
}
