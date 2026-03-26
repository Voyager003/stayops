package com.stayops.settlement.api.dto

import com.stayops.settlement.application.dto.DailySettlement
import java.math.BigDecimal
import java.time.LocalDate

data class DailySettlementResponse(
    val date: LocalDate,
    val reservationCount: Int,
    val revenue: BigDecimal,
    val commission: BigDecimal,
    val netAmount: BigDecimal
) {
    companion object {
        fun from(d: DailySettlement) = DailySettlementResponse(
            date = d.date,
            reservationCount = d.reservationCount,
            revenue = d.revenue.amount,
            commission = d.commission.amount,
            netAmount = d.netAmount.amount
        )
    }
}
