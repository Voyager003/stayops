package com.stayops.settlement.application.dto

import com.stayops.shared.domain.Money

data class ChannelSettlement(
    val channelCode: String,
    val reservationCount: Int,
    val totalRevenue: Money,
    val totalCommission: Money,
    val netSettlement: Money
)
