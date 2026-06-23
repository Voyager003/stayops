package com.stayops.inventory.application.required

import java.time.LocalDate

interface AvailabilitySyncRequester {
    fun requestAvailabilitySync(propertyId: String, roomTypeId: String, date: LocalDate, availableCount: Int)
}
