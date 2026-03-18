package com.stayops.settlement.api.dto

import com.stayops.settlement.application.dto.ChannelSettlement
import com.stayops.settlement.application.dto.SettlementSummary
import java.math.BigDecimal
import java.time.LocalDate

data class SettlementResponse(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalReservations: Int,
    val totalRevenue: BigDecimal,
    val totalCommission: BigDecimal,
    val netSettlement: BigDecimal,
    val byChannel: List<ChannelSettlementResponse>
) {
    data class ChannelSettlementResponse(
        val channelCode: String,
        val reservationCount: Int,
        val totalRevenue: BigDecimal,
        val totalCommission: BigDecimal,
        val netSettlement: BigDecimal
    )

    companion object {
        fun from(summary: SettlementSummary) = SettlementResponse(
            startDate = summary.startDate,
            endDate = summary.endDate,
            totalReservations = summary.totalReservations,
            totalRevenue = summary.totalRevenue.amount,
            totalCommission = summary.totalCommission.amount,
            netSettlement = summary.netSettlement.amount,
            byChannel = summary.byChannel.map { it.toResponse() }
        )

        private fun ChannelSettlement.toResponse() = ChannelSettlementResponse(
            channelCode = channelCode,
            reservationCount = reservationCount,
            totalRevenue = totalRevenue.amount,
            totalCommission = totalCommission.amount,
            netSettlement = netSettlement.amount
        )
    }
}
