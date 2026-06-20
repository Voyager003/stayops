package com.stayops.inventory.application.service

import java.time.LocalDate

interface InventoryReservationService {
    fun reserve(propertyId: String, roomTypeId: String, date: LocalDate)

    fun release(propertyId: String, roomTypeId: String, date: LocalDate)
}
