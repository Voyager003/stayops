package com.stayops.settlement.application.service

import com.stayops.settlement.application.dto.ChannelSettlement
import com.stayops.settlement.application.dto.DailySettlement
import com.stayops.settlement.application.dto.MonthlySettlement
import java.time.LocalDate

interface SettlementQueryRepository {

    fun findChannelSettlements(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ChannelSettlement>

    fun findDailyTrend(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailySettlement>

    fun findMonthlyTrend(
        propertyId: String,
        year: Int
    ): List<MonthlySettlement>
}
