package com.stayops.settlement.application.dto

import com.stayops.shared.domain.Money
import java.time.LocalDate

data class DailySettlement(
    val date: LocalDate,
    val reservationCount: Int,
    val revenue: Money,
    val commission: Money,
    val netAmount: Money
)
