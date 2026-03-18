package com.stayops.settlement.application.dto

import com.stayops.shared.domain.Money
import java.time.LocalDate

data class SettlementSummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalReservations: Int,
    val totalRevenue: Money,
    val totalCommission: Money,
    val netSettlement: Money,
    val byChannel: List<ChannelSettlement>
)
