package com.stayops.booking.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class CreateBookingRequest(
    @field:NotBlank
    val propertyId: String,

    @field:NotBlank
    val roomTypeId: String,

    @field:NotNull
    val checkIn: LocalDate,

    @field:NotNull
    val checkOut: LocalDate,

    @field:Min(1)
    val numberOfGuests: Int,

    @field:NotBlank
    val guestName: String,

    @field:NotBlank
    val guestPhone: String,

    val guestEmail: String? = null
)

data class PaymentConfirmRequest(
    @field:NotBlank
    val paymentKey: String,

    @field:NotBlank
    val orderId: String,

    @field:NotNull
    val amount: BigDecimal
)
