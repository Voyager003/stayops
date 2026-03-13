package com.stayops.rate.domain.service

import com.stayops.rate.domain.model.RatePlan
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import java.time.LocalDate

class RateResolver {

    fun resolve(
        ratePlans: List<RatePlan>,
        roomTypeBasePrice: Money,
        date: LocalDate,
        channelCode: String?
    ): Money {
        val applicable = ratePlans
            .filter { it.isApplicableTo(date, channelCode) }
            .sortedByDescending { it.priority }
            .firstOrNull()
            ?: return roomTypeBasePrice

        return applicable.priceForDate(date)
    }

    fun resolveForDateRange(
        ratePlans: List<RatePlan>,
        roomTypeBasePrice: Money,
        dateRange: DateRange,
        channelCode: String?
    ): Money {
        return dateRange.allDates().fold(Money.ZERO) { total, date ->
            total.add(resolve(ratePlans, roomTypeBasePrice, date, channelCode))
        }
    }
}
