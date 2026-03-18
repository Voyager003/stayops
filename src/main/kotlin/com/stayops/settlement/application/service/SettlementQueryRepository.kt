package com.stayops.settlement.application.service

import com.stayops.settlement.application.dto.ChannelSettlement
import java.time.LocalDate

interface SettlementQueryRepository {

    fun findChannelSettlements(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ChannelSettlement>
}
