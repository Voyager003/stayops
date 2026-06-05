package com.stayops.inventory.application.port

import java.time.LocalDate

interface AvailabilitySyncPort {
    fun requestAvailabilitySync(propertyId: String, roomTypeId: String, date: LocalDate, availableCount: Int)
}
