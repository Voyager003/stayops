package com.stayops.shared.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

@ConsistentCopyVisibility
data class DateRange private constructor(
    val checkIn: LocalDate,
    val checkOut: LocalDate
) {
    init {
        require(checkOut.isAfter(checkIn)) {
            "체크아웃은 체크인보다 이후여야 합니다: checkIn=$checkIn, checkOut=$checkOut"
        }
    }

    fun nights(): Long {
        return ChronoUnit.DAYS.between(checkIn, checkOut)
    }

    fun allDates(): List<LocalDate> {
        return checkIn.datesUntil(checkOut).toList()
    }

    fun overlaps(other: DateRange): Boolean {
        return checkIn.isBefore(other.checkOut) && other.checkIn.isBefore(checkOut)
    }

    companion object {
        fun of(checkIn: LocalDate, checkOut: LocalDate): DateRange {
            return DateRange(checkIn, checkOut)
        }
    }
}
