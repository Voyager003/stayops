package com.stayops.statistics.api.dto

import com.stayops.statistics.application.dto.MonthlyStatistics
import java.math.BigDecimal

data class MonthlyStatisticsResponse(
    val year: Int,
    val month: Int,
    val reservationCount: Int,
    val totalRevenue: BigDecimal,
    val cancelCount: Int,
    val cancelAmount: BigDecimal,
    val byChannel: List<ChannelStatisticsResponse>,
    val byRoom: List<RoomStatisticsResponse>,
    val roomAnalysis: List<RoomAnalysisResponse>
) {
    data class ChannelStatisticsResponse(
        val channelCode: String,
        val channelName: String,
        val amount: BigDecimal,
        val percentage: BigDecimal
    )

    data class RoomStatisticsResponse(
        val roomNumber: String,
        val amount: BigDecimal,
        val percentage: BigDecimal
    )

    data class RoomAnalysisResponse(
        val roomName: String,
        val totalAmount: BigDecimal,
        val adr: BigDecimal,
        val occupancyRate: BigDecimal
    )

    companion object {
        fun from(stats: MonthlyStatistics) = MonthlyStatisticsResponse(
            year = stats.year,
            month = stats.month,
            reservationCount = stats.reservationCount,
            totalRevenue = stats.totalRevenue.amount,
            cancelCount = stats.cancelCount,
            cancelAmount = stats.cancelAmount.amount,
            byChannel = stats.byChannel.map {
                ChannelStatisticsResponse(
                    channelCode = it.channelCode,
                    channelName = it.channelName,
                    amount = it.amount.amount,
                    percentage = it.percentage
                )
            },
            byRoom = stats.byRoom.map {
                RoomStatisticsResponse(
                    roomNumber = it.roomNumber,
                    amount = it.amount.amount,
                    percentage = it.percentage
                )
            },
            roomAnalysis = stats.roomAnalysis.map {
                RoomAnalysisResponse(
                    roomName = it.roomName,
                    totalAmount = it.totalAmount.amount,
                    adr = it.adr.amount,
                    occupancyRate = it.occupancyRate
                )
            }
        )
    }
}
