package com.stayops.reservation.domain.event

import com.stayops.shared.domain.DateRange

data class ReservationCreated(
    val reservationId: String,
    val propertyId: String,
    val roomTypeId: String,
    val dateRange: DateRange,
    val channelCode: String
)
