package com.stayops.settlement.application.service

import com.stayops.settlement.application.dto.ChannelSettlement
import com.stayops.settlement.application.dto.DailySettlement
import com.stayops.settlement.application.dto.MonthlySettlement
import com.stayops.settlement.application.dto.SettlementSummary
import com.stayops.shared.domain.Money
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SettlementQueryApplication(
    private val settlementQueryRepository: SettlementQueryRepository
) {

    fun getSettlementSummary(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): SettlementSummary {
        val channelSettlements = settlementQueryRepository.findChannelSettlements(
            propertyId, startDate, endDate
        )
        val documentCount = settlementQueryRepository.countReservations(propertyId, startDate, endDate)

        return buildSummary(channelSettlements, documentCount, startDate, endDate)
    }

    fun getSettlementSummaryByPropertyIds(
        propertyIds: List<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): SettlementSummary {
        val channelSettlements = settlementQueryRepository.findChannelSettlementsByPropertyIds(
            propertyIds, startDate, endDate
        )
        val documentCount = channelSettlements.sumOf { it.reservationCount }

        return buildSummary(channelSettlements, documentCount, startDate, endDate)
    }

    private fun buildSummary(
        channelSettlements: List<ChannelSettlement>,
        documentCount: Int,
        startDate: LocalDate,
        endDate: LocalDate
    ): SettlementSummary {
        val totalReservations = channelSettlements.sumOf { it.reservationCount }
        val totalRevenue = channelSettlements.fold(Money.ZERO) { acc, cs -> acc.add(cs.totalRevenue) }
        val totalCommission = channelSettlements.fold(Money.ZERO) { acc, cs -> acc.add(cs.totalCommission) }
        val netSettlement = channelSettlements.fold(Money.ZERO) { acc, cs -> acc.add(cs.netSettlement) }

        return SettlementSummary(
            startDate = startDate,
            endDate = endDate,
            totalReservations = totalReservations,
            totalRevenue = totalRevenue,
            totalCommission = totalCommission,
            netSettlement = netSettlement,
            documentCount = documentCount,
            byChannel = channelSettlements
        )
    }

    fun getDailyTrend(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailySettlement> = settlementQueryRepository.findDailyTrend(propertyId, startDate, endDate)

    fun getMonthlyTrend(
        propertyId: String,
        year: Int
    ): List<MonthlySettlement> = settlementQueryRepository.findMonthlyTrend(propertyId, year)
}
