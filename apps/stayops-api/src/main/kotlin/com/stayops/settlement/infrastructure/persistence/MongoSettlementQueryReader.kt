package com.stayops.settlement.infrastructure.persistence

import com.stayops.shared.config.MongoPersistence

import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.settlement.application.dto.ChannelSettlement
import com.stayops.settlement.application.dto.DailySettlement
import com.stayops.settlement.application.dto.MonthlySettlement
import com.stayops.settlement.application.required.SettlementQueryReader
import com.stayops.shared.domain.Money
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@MongoPersistence
@Repository
class MongoSettlementQueryReader(
    private val mongoTemplate: MongoTemplate
) : SettlementQueryReader {

    override fun findChannelSettlements(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ChannelSettlement> {
        val matchOp = Aggregation.match(
            Criteria.where("propertyId").`is`(propertyId)
                .and("status").`in`(ReservationStatus.CHECKED_OUT.name, ReservationStatus.NO_SHOW.name)
                .and("dateRange.checkOut").gte(startDate.toString()).lte(endDate.toString())
        )

        val groupOp = Aggregation.group("channel.channelCode")
            .count().`as`("reservationCount")
            .sum("pricing.totalAmount").`as`("totalRevenue")
            .sum("pricing.commissionAmount").`as`("totalCommission")
            .sum("pricing.netAmount").`as`("netSettlement")

        val aggregation = Aggregation.newAggregation(matchOp, groupOp)
        val results = mongoTemplate.aggregate(aggregation, "reservations", AggregationResult::class.java)

        return results.mappedResults.map { row ->
            ChannelSettlement(
                channelCode = row.id,
                reservationCount = row.reservationCount,
                totalRevenue = Money.won(row.totalRevenue),
                totalCommission = Money.won(row.totalCommission),
                netSettlement = Money.won(row.netSettlement)
            )
        }
    }

    override fun findChannelSettlementsByPropertyIds(
        propertyIds: List<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ChannelSettlement> {
        val matchOp = Aggregation.match(
            Criteria.where("propertyId").`in`(propertyIds)
                .and("status").`in`(ReservationStatus.CHECKED_OUT.name, ReservationStatus.NO_SHOW.name)
                .and("dateRange.checkOut").gte(startDate.toString()).lte(endDate.toString())
        )

        val groupOp = Aggregation.group("channel.channelCode")
            .count().`as`("reservationCount")
            .sum("pricing.totalAmount").`as`("totalRevenue")
            .sum("pricing.commissionAmount").`as`("totalCommission")
            .sum("pricing.netAmount").`as`("netSettlement")

        val aggregation = Aggregation.newAggregation(matchOp, groupOp)
        val results = mongoTemplate.aggregate(aggregation, "reservations", AggregationResult::class.java)

        return results.mappedResults.map { row ->
            ChannelSettlement(
                channelCode = row.id,
                reservationCount = row.reservationCount,
                totalRevenue = Money.won(row.totalRevenue),
                totalCommission = Money.won(row.totalCommission),
                netSettlement = Money.won(row.netSettlement)
            )
        }
    }

    override fun countReservations(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        val matchOp = Aggregation.match(
            Criteria.where("propertyId").`is`(propertyId)
                .and("status").`in`(ReservationStatus.CHECKED_OUT.name, ReservationStatus.NO_SHOW.name)
                .and("dateRange.checkOut").gte(startDate.toString()).lte(endDate.toString())
        )

        val countOp = Aggregation.count().`as`("total")

        val aggregation = Aggregation.newAggregation(matchOp, countOp)
        val results = mongoTemplate.aggregate(aggregation, "reservations", CountResult::class.java)

        return results.mappedResults.firstOrNull()?.total ?: 0
    }

    override fun findDailyTrend(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailySettlement> {
        val matchOp = Aggregation.match(
            Criteria.where("propertyId").`is`(propertyId)
                .and("status").`in`(ReservationStatus.CHECKED_OUT.name, ReservationStatus.NO_SHOW.name)
                .and("dateRange.checkOut").gte(startDate.toString()).lte(endDate.toString())
        )
        val groupOp = Aggregation.group("dateRange.checkOut")
            .count().`as`("reservationCount")
            .sum("pricing.totalAmount").`as`("revenue")
            .sum("pricing.commissionAmount").`as`("commission")
            .sum("pricing.netAmount").`as`("netAmount")
        val sortOp = Aggregation.sort(Sort.Direction.ASC, "_id")

        val aggregation = Aggregation.newAggregation(matchOp, groupOp, sortOp)
        val results = mongoTemplate.aggregate(aggregation, "reservations", DailyAggregationResult::class.java)

        return results.mappedResults.map { row ->
            DailySettlement(
                date = LocalDate.parse(row.id),
                reservationCount = row.reservationCount,
                revenue = Money.won(row.revenue),
                commission = Money.won(row.commission),
                netAmount = Money.won(row.netAmount)
            )
        }
    }

    override fun findMonthlyTrend(
        propertyId: String,
        year: Int
    ): List<MonthlySettlement> {
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)

        val matchOp = Aggregation.match(
            Criteria.where("propertyId").`is`(propertyId)
                .and("status").`in`(ReservationStatus.CHECKED_OUT.name, ReservationStatus.NO_SHOW.name)
                .and("dateRange.checkOut").gte(yearStart.toString()).lte(yearEnd.toString())
        )
        val projectOp = Aggregation.project()
            .and("dateRange.checkOut").substring(0, 7).`as`("yearMonth")
            .and("pricing.totalAmount").`as`("totalAmount")
            .and("pricing.commissionAmount").`as`("commissionAmount")
            .and("pricing.netAmount").`as`("netAmount")
        val groupOp = Aggregation.group("yearMonth")
            .count().`as`("reservationCount")
            .sum("totalAmount").`as`("revenue")
            .sum("commissionAmount").`as`("commission")
            .sum("netAmount").`as`("netAmount")
        val sortOp = Aggregation.sort(Sort.Direction.ASC, "_id")

        val aggregation = Aggregation.newAggregation(matchOp, projectOp, groupOp, sortOp)
        val results = mongoTemplate.aggregate(aggregation, "reservations", MonthlyAggregationResult::class.java)

        return results.mappedResults.map { row ->
            val parts = row.id.split("-")
            MonthlySettlement(
                year = parts[0].toInt(),
                month = parts[1].toInt(),
                reservationCount = row.reservationCount,
                revenue = Money.won(row.revenue),
                commission = Money.won(row.commission),
                netAmount = Money.won(row.netAmount)
            )
        }
    }

    private data class CountResult(
        val total: Int
    )

    private data class AggregationResult(
        val id: String,
        val reservationCount: Int,
        val totalRevenue: BigDecimal,
        val totalCommission: BigDecimal,
        val netSettlement: BigDecimal
    )

    private data class DailyAggregationResult(
        val id: String,
        val reservationCount: Int,
        val revenue: BigDecimal,
        val commission: BigDecimal,
        val netAmount: BigDecimal
    )

    private data class MonthlyAggregationResult(
        val id: String,
        val reservationCount: Int,
        val revenue: BigDecimal,
        val commission: BigDecimal,
        val netAmount: BigDecimal
    )
}
