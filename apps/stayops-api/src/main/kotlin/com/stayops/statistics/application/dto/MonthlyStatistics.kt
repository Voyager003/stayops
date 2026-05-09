package com.stayops.statistics.application.dto

import com.stayops.shared.domain.Money
import java.math.BigDecimal

data class MonthlyStatistics(
    val year: Int,
    val month: Int,
    val reservationCount: Int,
    val totalRevenue: Money,
    val cancelCount: Int,
    val cancelAmount: Money,
    val byChannel: List<ChannelStatistics>,
    val byRoom: List<RoomStatistics>,
    val roomAnalysis: List<RoomAnalysis>
)

data class ChannelStatistics(
    val channelCode: String,
    val channelName: String,
    val amount: Money,
    val percentage: BigDecimal
)

data class RoomStatistics(
    val roomNumber: String,
    val amount: Money,
    val percentage: BigDecimal
)

data class RoomAnalysis(
    val roomName: String,
    val totalAmount: Money,
    val adr: Money,
    val occupancyRate: BigDecimal
)
