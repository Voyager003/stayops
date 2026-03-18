package com.stayops.settlement.infrastructure.persistence

import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.settlement.application.dto.ChannelSettlement
import com.stayops.settlement.application.service.SettlementQueryRepository
import com.stayops.shared.domain.Money
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class MongoSettlementQueryRepository(
    private val mongoTemplate: MongoTemplate
) : SettlementQueryRepository {

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

    private data class AggregationResult(
        val id: String,
        val reservationCount: Int,
        val totalRevenue: BigDecimal,
        val totalCommission: BigDecimal,
        val netSettlement: BigDecimal
    )
}
