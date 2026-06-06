package com.stayops.reservation.api.customer.dto

import com.stayops.reservation.application.service.CustomerBookingDraft
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class CreateBookingDraftRequest(
    @field:NotBlank
    val propertyId: String,

    @field:NotBlank
    val roomTypeId: String,

    @field:NotNull
    val checkIn: LocalDate,

    @field:NotNull
    val checkOut: LocalDate,

    @field:Min(1)
    val guests: Int
)

data class BookingDraftResponse(
    val draftId: String,
    val propertyId: String,
    val roomTypeId: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guests: Int
) {
    companion object {
        fun from(draft: CustomerBookingDraft): BookingDraftResponse =
            BookingDraftResponse(
                draftId = draft.draftId,
                propertyId = draft.propertyId,
                roomTypeId = draft.roomTypeId,
                checkIn = draft.checkIn,
                checkOut = draft.checkOut,
                guests = draft.guests
            )
    }
}
