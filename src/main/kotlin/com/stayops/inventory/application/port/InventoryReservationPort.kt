package com.stayops.inventory.application.port

import java.time.LocalDate

interface InventoryReservationPort {
    fun reserve(propertyId: String, roomTypeId: String, date: LocalDate)

    fun release(propertyId: String, roomTypeId: String, date: LocalDate)
}
