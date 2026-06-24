package com.stayops.statistics.application.service

import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.domain.Money
import com.stayops.statistics.application.dto.ChannelStatistics
import com.stayops.statistics.application.dto.MonthlyStatistics
import com.stayops.statistics.application.dto.RoomAnalysis
import com.stayops.statistics.application.dto.RoomStatistics
import com.stayops.statistics.application.required.StatisticsQueryReader
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

@Service
class StatisticsApplication(
    private val statisticsQueryRepository: StatisticsQueryReader,
    private val channelRepository: ChannelRepository,
    private val roomRepository: RoomRepository
) {

    fun getMonthlyStatistics(propertyId: String, year: Int, month: Int): MonthlyStatistics {
        val ym = YearMonth.of(year, month)
        val startDate = ym.atDay(1)
        val endDate = ym.atEndOfMonth()

        return buildStatistics(propertyId, year, month, startDate, endDate)
    }

    fun getAnnualStatistics(propertyId: String, year: Int): List<MonthlyStatistics> {
        return (1..12).map { month ->
            val ym = YearMonth.of(year, month)
            val startDate = ym.atDay(1)
            val endDate = ym.atEndOfMonth()
            buildStatistics(propertyId, year, month, startDate, endDate)
        }
    }

    private fun buildStatistics(
        propertyId: String,
        year: Int,
        month: Int,
        startDate: LocalDate,
        endDate: LocalDate
    ): MonthlyStatistics {
        val reservationCount = statisticsQueryRepository.countCompletedReservations(propertyId, startDate, endDate)
        val totalRevenueAmount = statisticsQueryRepository.totalRevenue(propertyId, startDate, endDate)
        val totalRevenue = Money.won(totalRevenueAmount)

        // Cancel aggregation
        val cancelAgg = statisticsQueryRepository.findCancelAggregation(propertyId, startDate, endDate)

        // Channel breakdown
        val channelAggs = statisticsQueryRepository.findChannelAggregation(propertyId, startDate, endDate)
        val channels = channelRepository.findByPropertyId(propertyId)
        val channelNameMap = channels.associate { it.code to it.name }

        val byChannel = channelAggs.map { agg ->
            val percentage = if (totalRevenueAmount > BigDecimal.ZERO) {
                agg.totalAmount.multiply(BigDecimal(100)).divide(totalRevenueAmount, 1, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            ChannelStatistics(
                channelCode = agg.channelCode,
                channelName = channelNameMap[agg.channelCode] ?: agg.channelCode,
                amount = Money.won(agg.totalAmount),
                percentage = percentage
            )
        }

        // Room breakdown
        val roomAggs = statisticsQueryRepository.findRoomAggregation(propertyId, startDate, endDate)
        val rooms = roomRepository.findByPropertyId(propertyId)
        val roomMap = rooms.associate { it.id to it }

        val totalDaysInMonth = endDate.dayOfMonth
        val totalRooms = rooms.size

        val byRoom = roomAggs.map { agg ->
            val room = roomMap[agg.roomId]
            val percentage = if (totalRevenueAmount > BigDecimal.ZERO) {
                agg.totalAmount.multiply(BigDecimal(100)).divide(totalRevenueAmount, 1, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            RoomStatistics(
                roomNumber = room?.roomNumber ?: agg.roomId,
                amount = Money.won(agg.totalAmount),
                percentage = percentage
            )
        }

        val roomAnalysis = roomAggs.map { agg ->
            val room = roomMap[agg.roomId]
            val adr = if (agg.totalNights > 0) {
                Money.won(agg.totalAmount.divide(BigDecimal(agg.totalNights), 0, RoundingMode.HALF_UP))
            } else Money.ZERO

            val occupancyRate = if (totalDaysInMonth > 0) {
                BigDecimal(agg.totalNights).multiply(BigDecimal(100))
                    .divide(BigDecimal(totalDaysInMonth), 1, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            RoomAnalysis(
                roomName = room?.roomNumber ?: agg.roomId,
                totalAmount = Money.won(agg.totalAmount),
                adr = adr,
                occupancyRate = occupancyRate
            )
        }

        return MonthlyStatistics(
            year = year,
            month = month,
            reservationCount = reservationCount,
            totalRevenue = totalRevenue,
            cancelCount = cancelAgg.cancelCount,
            cancelAmount = Money.won(cancelAgg.cancelAmount),
            byChannel = byChannel,
            byRoom = byRoom,
            roomAnalysis = roomAnalysis
        )
    }
}
