package com.stayops.settlement.api.dto

import com.stayops.settlement.application.dto.MonthlySettlement
import java.math.BigDecimal

data class MonthlySettlementResponse(
    val year: Int,
    val month: Int,
    val reservationCount: Int,
    val revenue: BigDecimal,
    val commission: BigDecimal,
    val netAmount: BigDecimal
) {
    companion object {
        fun from(m: MonthlySettlement) = MonthlySettlementResponse(
            year = m.year,
            month = m.month,
            reservationCount = m.reservationCount,
            revenue = m.revenue.amount,
            commission = m.commission.amount,
            netAmount = m.netAmount.amount
        )
    }
}
