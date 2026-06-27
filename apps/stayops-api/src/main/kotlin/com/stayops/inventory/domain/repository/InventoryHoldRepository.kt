package com.stayops.inventory.domain.repository

import com.stayops.inventory.domain.model.InventoryHold
import java.time.Instant
import java.time.LocalDate

interface InventoryHoldRepository {
    fun save(hold: InventoryHold): InventoryHold
    fun findById(id: String): InventoryHold?
    fun findByReservationIntentId(reservationIntentId: String): InventoryHold?
    fun findActiveByPropertyIdAndRoomTypeIdAndDates(
        propertyId: String,
        roomTypeId: String,
        dates: List<LocalDate>,
        now: Instant
    ): List<InventoryHold>
}
