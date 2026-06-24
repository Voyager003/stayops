package com.stayops.statistics.application.required

import java.math.BigDecimal
import java.time.LocalDate

interface StatisticsQueryReader {

    fun findChannelAggregation(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ChannelAggregation>

    fun findRoomAggregation(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RoomAggregation>

    fun findCancelAggregation(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): CancelAggregation

    fun countCompletedReservations(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int

    fun totalRevenue(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): BigDecimal

    data class ChannelAggregation(
        val channelCode: String,
        val totalAmount: BigDecimal,
        val count: Int
    )

    data class RoomAggregation(
        val roomId: String,
        val totalAmount: BigDecimal,
        val totalNights: Int
    )

    data class CancelAggregation(
        val cancelCount: Int,
        val cancelAmount: BigDecimal
    )
}
