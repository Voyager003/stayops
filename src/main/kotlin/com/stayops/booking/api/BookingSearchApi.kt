package com.stayops.booking.api

import com.stayops.booking.api.dto.*
import com.stayops.booking.application.service.BookingSearchApplication
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/booking/properties")
class BookingSearchApi(
    private val bookingSearchApplication: BookingSearchApplication
) {

    @GetMapping
    fun searchProperties(): ResponseEntity<List<PropertySearchResponse>> {
        val properties = bookingSearchApplication.searchProperties()
        return ResponseEntity.ok(properties.map { PropertySearchResponse.from(it) })
    }

    @GetMapping("/{propertyId}")
    fun getProperty(@PathVariable propertyId: String): ResponseEntity<PropertySearchResponse> {
        val property = bookingSearchApplication.getProperty(propertyId)
        return ResponseEntity.ok(PropertySearchResponse.from(property))
    }

    @GetMapping("/{propertyId}/room-types")
    fun searchRoomTypes(@PathVariable propertyId: String): ResponseEntity<List<RoomTypeSearchResponse>> {
        val roomTypes = bookingSearchApplication.searchRoomTypes(propertyId)
        return ResponseEntity.ok(roomTypes.map { RoomTypeSearchResponse.from(it) })
    }

    @GetMapping("/{propertyId}/room-types/{roomTypeId}/availability")
    fun getAvailability(
        @PathVariable propertyId: String,
        @PathVariable roomTypeId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkIn: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkOut: LocalDate
    ): ResponseEntity<List<AvailabilityResponse>> {
        val inventories = bookingSearchApplication.getAvailability(propertyId, roomTypeId, checkIn, checkOut)
        return ResponseEntity.ok(inventories.map { AvailabilityResponse.from(it) })
    }

    @GetMapping("/{propertyId}/room-types/{roomTypeId}/rates")
    fun getRatePreview(
        @PathVariable propertyId: String,
        @PathVariable roomTypeId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkIn: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkOut: LocalDate
    ): ResponseEntity<RatePreviewResponse> {
        val totalAmount = bookingSearchApplication.getRatePreview(propertyId, roomTypeId, checkIn, checkOut)
        return ResponseEntity.ok(RatePreviewResponse.of(totalAmount, checkIn, checkOut))
    }
}
