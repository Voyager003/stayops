package com.stayops.inventory.application.provided

import com.stayops.shared.domain.DateRange
import java.time.Instant
import java.time.LocalDate

interface InventoryHoldService {
    fun hold(
        reservationIntentId: String,
        propertyId: String,
        roomTypeId: String,
        dateRange: DateRange,
        quantity: Int,
        expiresAt: Instant
    ): InventoryHoldSnapshot

    fun consume(reservationIntentId: String)

    fun release(reservationIntentId: String)
}

data class InventoryHoldSnapshot(
    val id: String,
    val reservationIntentId: String,
    val propertyId: String,
    val roomTypeId: String,
    val dates: List<LocalDate>,
    val quantity: Int,
    val expiresAt: Instant
)
