package com.stayops.reservation.domain.model

import java.time.LocalDate

data class ReservationSearchCriteria(
    val statuses: List<ReservationStatus>? = null,
    val roomTypeId: String? = null,
    val channelCodes: List<String>? = null,
    val dateType: DateType? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val guestName: String? = null
)

enum class DateType {
    CHECK_IN, CHECK_OUT, CREATED
}
