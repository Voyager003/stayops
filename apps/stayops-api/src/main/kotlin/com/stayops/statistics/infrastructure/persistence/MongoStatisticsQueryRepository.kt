package com.stayops.statistics.infrastructure.persistence

import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.statistics.application.service.StatisticsQueryRepository
import com.stayops.statistics.application.service.StatisticsQueryRepository.CancelAggregation
import com.stayops.statistics.application.service.StatisticsQueryRepository.ChannelAggregation
import com.stayops.statistics.application.service.StatisticsQueryRepository.RoomAggregation
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
class MongoStatisticsQueryRepository(
    private val mongoTemplate: MongoTemplate
) : StatisticsQueryRepository {

    private val completedStatuses = listOf(
        ReservationStatus.CHECKED_OUT.name,
        ReservationStatus.NO_SHOW.name
    )

    override fun findChannelAggregation(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ChannelAggregation> {
        val matchOp = Aggregation.match(
            Criteria.where("propertyId").`is`(propertyId)
                .and("status").`in`(completedStatuses)
                .and("dateRange.checkOut").gte(startDate.toString()).lte(endDate.toString())
        )
        val groupOp = Aggregation.group("channel.channelCode")
            .sum("pricing.totalAmount").`as`("totalAmount")
            .count().`as`("count")

        val aggregation = Aggregation.newAggregation(matchOp, groupOp)
        val results = mongoTemplate.aggregate(aggregation, "reservations", ChannelAggResult::class.java)

        return results.mappedResults.map {
            ChannelAggregation(
                channelCode = it.id,
                totalAmount = it.totalAmount,
                count = it.count
            )
        }
    }

    override fun findRoomAggregation(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RoomAggregation> {
        val matchOp = Aggregation.match(
            Criteria.where("propertyId").`is`(propertyId)
                .and("status").`in`(completedStatuses)
                .and("dateRange.checkOut").gte(startDate.toString()).lte(endDate.toString())
                .and("roomId").ne(null)
        )
        val groupOp = Aggregation.group("roomId")
            .sum("pricing.totalAmount").`as`("totalAmount")
            .sum("nightCount").`as`("totalNights")

        val aggregation = Aggregation.newAggregation(matchOp, groupOp)
        val results = mongoTemplate.aggregate(aggregation, "reservations", RoomAggResult::class.java)

        return results.mappedResults.map {
            RoomAggregation(
                roomId = it.id,
                totalAmount = it.totalAmount,
                totalNights = it.totalNights
            )
        }
    }

    override fun findCancelAggregation(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): CancelAggregation {
        val matchOp = Aggregation.match(
            Criteria.where("propertyId").`is`(propertyId)
                .and("status").`is`(ReservationStatus.CANCELLED.name)
                .and("dateRange.checkIn").gte(startDate.toString()).lte(endDate.toString())
        )
        val groupOp = Aggregation.group()
            .count().`as`("cancelCount")
            .sum("pricing.totalAmount").`as`("cancelAmount")

        val aggregation = Aggregation.newAggregation(matchOp, groupOp)
        val results = mongoTemplate.aggregate(aggregation, "reservations", CancelAggResult::class.java)

        val result = results.mappedResults.firstOrNull()
        return CancelAggregation(
            cancelCount = result?.cancelCount ?: 0,
            cancelAmount = result?.cancelAmount ?: BigDecimal.ZERO
        )
    }

    override fun countCompletedReservations(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        val matchOp = Aggregation.match(
            Criteria.where("propertyId").`is`(propertyId)
                .and("status").`in`(completedStatuses)
                .and("dateRange.checkOut").gte(startDate.toString()).lte(endDate.toString())
        )
        val countOp = Aggregation.count().`as`("total")

        val aggregation = Aggregation.newAggregation(matchOp, countOp)
        val results = mongoTemplate.aggregate(aggregation, "reservations", CountAggResult::class.java)

        return results.mappedResults.firstOrNull()?.total ?: 0
    }

    override fun totalRevenue(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): BigDecimal {
        val matchOp = Aggregation.match(
            Criteria.where("propertyId").`is`(propertyId)
                .and("status").`in`(completedStatuses)
                .and("dateRange.checkOut").gte(startDate.toString()).lte(endDate.toString())
        )
        val groupOp = Aggregation.group()
            .sum("pricing.totalAmount").`as`("totalRevenue")

        val aggregation = Aggregation.newAggregation(matchOp, groupOp)
        val results = mongoTemplate.aggregate(aggregation, "reservations", RevenueAggResult::class.java)

        return results.mappedResults.firstOrNull()?.totalRevenue ?: BigDecimal.ZERO
    }

    private data class ChannelAggResult(
        val id: String,
        val totalAmount: BigDecimal,
        val count: Int
    )

    private data class RoomAggResult(
        val id: String,
        val totalAmount: BigDecimal,
        val totalNights: Int
    )

    private data class CancelAggResult(
        val cancelCount: Int,
        val cancelAmount: BigDecimal
    )

    private data class CountAggResult(
        val total: Int
    )

    private data class RevenueAggResult(
        val totalRevenue: BigDecimal
    )
}
