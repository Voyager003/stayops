package com.stayops.statistics.infrastructure.persistence

import com.stayops.jooq.generated.Tables.RESERVATIONS
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.shared.config.RdbPersistence
import com.stayops.statistics.application.required.StatisticsQueryReader
import com.stayops.statistics.application.required.StatisticsQueryReader.CancelAggregation
import com.stayops.statistics.application.required.StatisticsQueryReader.ChannelAggregation
import com.stayops.statistics.application.required.StatisticsQueryReader.RoomAggregation
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@RdbPersistence
@Repository
class RdbStatisticsQueryReader(
    private val dsl: DSLContext
) : StatisticsQueryReader {

    private val completedStatuses = listOf(
        ReservationStatus.CHECKED_OUT.name,
        ReservationStatus.NO_SHOW.name
    )

    override fun findChannelAggregation(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ChannelAggregation> =
        dsl.select(
            RESERVATIONS.CHANNEL_CODE,
            DSL.coalesce(DSL.sum(RESERVATIONS.TOTAL_AMOUNT), BigDecimal.ZERO),
            DSL.count()
        )
            .from(RESERVATIONS)
            .where(completedCondition(propertyId, startDate, endDate))
            .groupBy(RESERVATIONS.CHANNEL_CODE)
            .orderBy(RESERVATIONS.CHANNEL_CODE.asc())
            .fetch { row ->
                ChannelAggregation(
                    channelCode = row.value1(),
                    totalAmount = row.value2(),
                    count = row.value3()
                )
            }

    override fun findRoomAggregation(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RoomAggregation> =
        dsl.select(
            RESERVATIONS.ROOM_ID,
            DSL.coalesce(DSL.sum(RESERVATIONS.TOTAL_AMOUNT), BigDecimal.ZERO),
            DSL.coalesce(DSL.sum(RESERVATIONS.NIGHT_COUNT), BigDecimal.ZERO)
        )
            .from(RESERVATIONS)
            .where(
                completedCondition(propertyId, startDate, endDate)
                    .and(RESERVATIONS.ROOM_ID.isNotNull)
            )
            .groupBy(RESERVATIONS.ROOM_ID)
            .orderBy(RESERVATIONS.ROOM_ID.asc())
            .fetch { row ->
                RoomAggregation(
                    roomId = row.value1(),
                    totalAmount = row.value2(),
                    totalNights = row.value3().toInt()
                )
            }

    override fun findCancelAggregation(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): CancelAggregation {
        val row = dsl.select(
            DSL.count(),
            DSL.coalesce(DSL.sum(RESERVATIONS.TOTAL_AMOUNT), BigDecimal.ZERO)
        )
            .from(RESERVATIONS)
            .where(RESERVATIONS.PROPERTY_ID.eq(propertyId))
            .and(RESERVATIONS.STATUS.eq(ReservationStatus.CANCELLED.name))
            .and(RESERVATIONS.CHECK_IN.ge(startDate))
            .and(RESERVATIONS.CHECK_IN.le(endDate))
            .fetchOne()

        return CancelAggregation(
            cancelCount = row?.value1() ?: 0,
            cancelAmount = row?.value2() ?: BigDecimal.ZERO
        )
    }

    override fun countCompletedReservations(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int =
        dsl.fetchCount(RESERVATIONS, completedCondition(propertyId, startDate, endDate))

    override fun totalRevenue(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): BigDecimal =
        dsl.select(DSL.coalesce(DSL.sum(RESERVATIONS.TOTAL_AMOUNT), BigDecimal.ZERO))
            .from(RESERVATIONS)
            .where(completedCondition(propertyId, startDate, endDate))
            .fetchOne(0, BigDecimal::class.java) ?: BigDecimal.ZERO

    private fun completedCondition(propertyId: String, startDate: LocalDate, endDate: LocalDate): Condition =
        RESERVATIONS.PROPERTY_ID.eq(propertyId)
            .and(RESERVATIONS.STATUS.`in`(completedStatuses))
            .and(RESERVATIONS.CHECK_OUT.ge(startDate))
            .and(RESERVATIONS.CHECK_OUT.le(endDate))
}
