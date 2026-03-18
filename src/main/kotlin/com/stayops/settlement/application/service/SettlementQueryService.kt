package com.stayops.settlement.application.service

import com.stayops.settlement.application.dto.SettlementSummary
import com.stayops.shared.domain.Money
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class SettlementQueryService(
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

        val totalReservations = channelSettlements.sumOf { it.reservationCount }
        val totalRevenue = channelSettlements.fold(Money.ZERO) { acc, it -> acc.add(it.totalRevenue) }
        val totalCommission = channelSettlements.fold(Money.ZERO) { acc, it -> acc.add(it.totalCommission) }
        val netSettlement = channelSettlements.fold(Money.ZERO) { acc, it -> acc.add(it.netSettlement) }

        return SettlementSummary(
            startDate = startDate,
            endDate = endDate,
            totalReservations = totalReservations,
            totalRevenue = totalRevenue,
            totalCommission = totalCommission,
            netSettlement = netSettlement,
            byChannel = channelSettlements
        )
    }
}
