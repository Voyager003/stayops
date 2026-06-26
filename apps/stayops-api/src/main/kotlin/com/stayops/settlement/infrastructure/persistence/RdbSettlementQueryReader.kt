package com.stayops.settlement.infrastructure.persistence

import com.stayops.jooq.generated.Tables.RESERVATIONS
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.settlement.application.dto.ChannelSettlement
import com.stayops.settlement.application.dto.DailySettlement
import com.stayops.settlement.application.dto.MonthlySettlement
import com.stayops.settlement.application.required.SettlementQueryReader
import com.stayops.shared.config.RdbPersistence
import com.stayops.shared.domain.Money
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@RdbPersistence
@Repository
class RdbSettlementQueryReader(
    private val dsl: DSLContext
) : SettlementQueryReader {

    private val completedStatuses = listOf(
        ReservationStatus.CHECKED_OUT.name,
        ReservationStatus.NO_SHOW.name
    )

    override fun findChannelSettlements(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ChannelSettlement> =
        findChannelSettlements(baseCondition(propertyId, startDate, endDate))

    override fun findChannelSettlementsByPropertyIds(
        propertyIds: List<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ChannelSettlement> {
        if (propertyIds.isEmpty()) return emptyList()
        return findChannelSettlements(
            RESERVATIONS.PROPERTY_ID.`in`(propertyIds)
                .and(completedCondition(startDate, endDate))
        )
    }

    override fun findDailyTrend(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailySettlement> =
        dsl.select(
            RESERVATIONS.CHECK_OUT,
            DSL.count(),
            DSL.coalesce(DSL.sum(RESERVATIONS.TOTAL_AMOUNT), BigDecimal.ZERO),
            DSL.coalesce(DSL.sum(RESERVATIONS.COMMISSION_AMOUNT), BigDecimal.ZERO),
            DSL.coalesce(DSL.sum(RESERVATIONS.NET_AMOUNT), BigDecimal.ZERO)
        )
            .from(RESERVATIONS)
            .where(baseCondition(propertyId, startDate, endDate))
            .groupBy(RESERVATIONS.CHECK_OUT)
            .orderBy(RESERVATIONS.CHECK_OUT.asc())
            .fetch { row ->
                DailySettlement(
                    date = row.value1(),
                    reservationCount = row.value2(),
                    revenue = Money.won(row.value3()),
                    commission = Money.won(row.value4()),
                    netAmount = Money.won(row.value5())
                )
            }

    override fun findMonthlyTrend(propertyId: String, year: Int): List<MonthlySettlement> {
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)

        return dsl.select(
            DSL.extract(RESERVATIONS.CHECK_OUT, org.jooq.DatePart.YEAR),
            DSL.extract(RESERVATIONS.CHECK_OUT, org.jooq.DatePart.MONTH),
            DSL.count(),
            DSL.coalesce(DSL.sum(RESERVATIONS.TOTAL_AMOUNT), BigDecimal.ZERO),
            DSL.coalesce(DSL.sum(RESERVATIONS.COMMISSION_AMOUNT), BigDecimal.ZERO),
            DSL.coalesce(DSL.sum(RESERVATIONS.NET_AMOUNT), BigDecimal.ZERO)
        )
            .from(RESERVATIONS)
            .where(baseCondition(propertyId, yearStart, yearEnd))
            .groupBy(
                DSL.extract(RESERVATIONS.CHECK_OUT, org.jooq.DatePart.YEAR),
                DSL.extract(RESERVATIONS.CHECK_OUT, org.jooq.DatePart.MONTH)
            )
            .orderBy(
                DSL.extract(RESERVATIONS.CHECK_OUT, org.jooq.DatePart.YEAR).asc(),
                DSL.extract(RESERVATIONS.CHECK_OUT, org.jooq.DatePart.MONTH).asc()
            )
            .fetch { row ->
                MonthlySettlement(
                    year = row.value1(),
                    month = row.value2(),
                    reservationCount = row.value3(),
                    revenue = Money.won(row.value4()),
                    commission = Money.won(row.value5()),
                    netAmount = Money.won(row.value6())
                )
            }
    }

    override fun countReservations(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int =
        dsl.fetchCount(RESERVATIONS, baseCondition(propertyId, startDate, endDate))

    private fun findChannelSettlements(condition: Condition): List<ChannelSettlement> =
        dsl.select(
            RESERVATIONS.CHANNEL_CODE,
            DSL.count(),
            DSL.coalesce(DSL.sum(RESERVATIONS.TOTAL_AMOUNT), BigDecimal.ZERO),
            DSL.coalesce(DSL.sum(RESERVATIONS.COMMISSION_AMOUNT), BigDecimal.ZERO),
            DSL.coalesce(DSL.sum(RESERVATIONS.NET_AMOUNT), BigDecimal.ZERO)
        )
            .from(RESERVATIONS)
            .where(condition)
            .groupBy(RESERVATIONS.CHANNEL_CODE)
            .orderBy(RESERVATIONS.CHANNEL_CODE.asc())
            .fetch { row ->
                ChannelSettlement(
                    channelCode = row.value1(),
                    reservationCount = row.value2(),
                    totalRevenue = Money.won(row.value3()),
                    totalCommission = Money.won(row.value4()),
                    netSettlement = Money.won(row.value5())
                )
            }

    private fun baseCondition(propertyId: String, startDate: LocalDate, endDate: LocalDate): Condition =
        RESERVATIONS.PROPERTY_ID.eq(propertyId)
            .and(completedCondition(startDate, endDate))

    private fun completedCondition(startDate: LocalDate, endDate: LocalDate): Condition =
        RESERVATIONS.STATUS.`in`(completedStatuses)
            .and(RESERVATIONS.CHECK_OUT.ge(startDate))
            .and(RESERVATIONS.CHECK_OUT.le(endDate))
}
