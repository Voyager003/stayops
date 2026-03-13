package com.stayops.guest.domain.model

import com.stayops.shared.domain.Money
import java.time.LocalDate

data class VisitSummary(
    val totalVisits: Int = 0,
    val totalSpend: Money = Money.ZERO,
    val lastVisitDate: LocalDate? = null,
    val averageStayNights: Double = 0.0
) {
    fun recordVisit(spend: Money, stayNights: Int, visitDate: LocalDate): VisitSummary {
        val newTotalVisits = totalVisits + 1
        val newTotalSpend = totalSpend.add(spend)
        val newAverageStayNights =
            (averageStayNights * totalVisits + stayNights) / newTotalVisits

        return copy(
            totalVisits = newTotalVisits,
            totalSpend = newTotalSpend,
            lastVisitDate = visitDate,
            averageStayNights = newAverageStayNights
        )
    }

    companion object {
        val EMPTY = VisitSummary()
    }
}
