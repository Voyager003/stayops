package com.stayops.settlement.application.dto

import com.stayops.shared.domain.Money

data class MonthlySettlement(
    val year: Int,
    val month: Int,
    val reservationCount: Int,
    val revenue: Money,
    val commission: Money,
    val netAmount: Money
)
