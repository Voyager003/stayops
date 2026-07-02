package com.stayops.rate.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.RATE_PLAN_DAY_OF_WEEK_RULES
import com.stayops.jooq.generated.Tables.RATE_PLANS
import com.stayops.jooq.generated.tables.records.RatePlansRecord
import com.stayops.rate.domain.model.DayOfWeekRate
import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.shared.config.RdbPersistence
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.ConflictException
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbRatePlanRepository(
    private val dsl: DSLContext
) : RatePlanRepository {

    override fun save(ratePlan: RatePlan): RatePlan =
        dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)

            val updatedRows = if (ratePlan.version == null) {
                tx.insertInto(RATE_PLANS)
                    .set(RATE_PLANS.ID, ratePlan.id)
                    .set(RATE_PLANS.PROPERTY_ID, ratePlan.propertyId)
                    .set(RATE_PLANS.ROOM_TYPE_ID, ratePlan.roomTypeId)
                    .set(RATE_PLANS.NAME, ratePlan.name)
                    .set(RATE_PLANS.TYPE, ratePlan.type.name)
                    .set(RATE_PLANS.DATE_RANGE_START, ratePlan.dateRange?.checkIn)
                    .set(RATE_PLANS.DATE_RANGE_END, ratePlan.dateRange?.checkOut)
                    .set(RATE_PLANS.CHANNEL_CODE, ratePlan.channelCode)
                    .set(RATE_PLANS.PRICE_AMOUNT, ratePlan.price.amount.toLong())
                    .set(RATE_PLANS.PRIORITY, ratePlan.priority)
                    .set(RATE_PLANS.STATUS, ratePlan.status.name)
                    .set(RATE_PLANS.VERSION, 0L)
                    .set(RATE_PLANS.CREATED_AT, ratePlan.createdAt.toOffsetDateTime())
                    .set(RATE_PLANS.UPDATED_AT, ratePlan.updatedAt.toOffsetDateTime())
                    .execute()
            } else {
                tx.update(RATE_PLANS)
                    .set(RATE_PLANS.PROPERTY_ID, ratePlan.propertyId)
                    .set(RATE_PLANS.ROOM_TYPE_ID, ratePlan.roomTypeId)
                    .set(RATE_PLANS.NAME, ratePlan.name)
                    .set(RATE_PLANS.TYPE, ratePlan.type.name)
                    .set(RATE_PLANS.DATE_RANGE_START, ratePlan.dateRange?.checkIn)
                    .set(RATE_PLANS.DATE_RANGE_END, ratePlan.dateRange?.checkOut)
                    .set(RATE_PLANS.CHANNEL_CODE, ratePlan.channelCode)
                    .set(RATE_PLANS.PRICE_AMOUNT, ratePlan.price.amount.toLong())
                    .set(RATE_PLANS.PRIORITY, ratePlan.priority)
                    .set(RATE_PLANS.STATUS, ratePlan.status.name)
                    .set(RATE_PLANS.VERSION, ratePlan.version!! + 1)
                    .set(RATE_PLANS.CREATED_AT, ratePlan.createdAt.toOffsetDateTime())
                    .set(RATE_PLANS.UPDATED_AT, ratePlan.updatedAt.toOffsetDateTime())
                    .where(RATE_PLANS.ID.eq(ratePlan.id))
                    .and(RATE_PLANS.VERSION.eq(ratePlan.version))
                    .execute()
            }

            if (updatedRows == 0 && ratePlan.version != null) {
                throw ConflictException(
                    code = "RATE_PLAN_CONFLICT",
                    message = "요금제 변경 충돌이 발생했습니다. 다시 시도해주세요."
                )
            }

            tx.deleteFrom(RATE_PLAN_DAY_OF_WEEK_RULES)
                .where(RATE_PLAN_DAY_OF_WEEK_RULES.RATE_PLAN_ID.eq(ratePlan.id))
                .execute()

            ratePlan.dayOfWeekRules?.forEach { rule ->
                rule.daysOfWeek.forEach { dayOfWeek ->
                    tx.insertInto(RATE_PLAN_DAY_OF_WEEK_RULES)
                        .set(RATE_PLAN_DAY_OF_WEEK_RULES.RATE_PLAN_ID, ratePlan.id)
                        .set(RATE_PLAN_DAY_OF_WEEK_RULES.DAY_OF_WEEK, dayOfWeek.name)
                        .set(RATE_PLAN_DAY_OF_WEEK_RULES.PRICE_AMOUNT, rule.price.amount.toLong())
                        .execute()
                }
            }

            tx.findRatePlanById(ratePlan.id) ?: ratePlan
        }

    override fun findById(id: String): RatePlan? =
        dsl.findRatePlanById(id)

    override fun findByPropertyIdAndRoomTypeIdAndStatus(
        propertyId: String,
        roomTypeId: String,
        status: RatePlanStatus
    ): List<RatePlan> =
        dsl.selectFrom(RATE_PLANS)
            .where(RATE_PLANS.PROPERTY_ID.eq(propertyId))
            .and(RATE_PLANS.ROOM_TYPE_ID.eq(roomTypeId))
            .and(RATE_PLANS.STATUS.eq(status.name))
            .orderBy(RATE_PLANS.PRIORITY.desc(), RATE_PLANS.ID.asc())
            .fetch { record -> dsl.toDomain(record) }

    override fun findByPropertyId(propertyId: String): List<RatePlan> =
        dsl.selectFrom(RATE_PLANS)
            .where(RATE_PLANS.PROPERTY_ID.eq(propertyId))
            .orderBy(RATE_PLANS.PRIORITY.desc(), RATE_PLANS.ID.asc())
            .fetch { record -> dsl.toDomain(record) }

    override fun deleteById(id: String) {
        dsl.transaction { configuration ->
            val tx = DSL.using(configuration)
            tx.deleteFrom(RATE_PLAN_DAY_OF_WEEK_RULES)
                .where(RATE_PLAN_DAY_OF_WEEK_RULES.RATE_PLAN_ID.eq(id))
                .execute()
            tx.deleteFrom(RATE_PLANS)
                .where(RATE_PLANS.ID.eq(id))
                .execute()
        }
    }

    private fun DSLContext.findRatePlanById(id: String): RatePlan? =
        selectFrom(RATE_PLANS)
            .where(RATE_PLANS.ID.eq(id))
            .fetchOne()
            ?.let { toDomain(it) }

    private fun DSLContext.toDomain(record: RatePlansRecord): RatePlan {
        val rules = selectFrom(RATE_PLAN_DAY_OF_WEEK_RULES)
            .where(RATE_PLAN_DAY_OF_WEEK_RULES.RATE_PLAN_ID.eq(record.get(RATE_PLANS.ID)))
            .orderBy(RATE_PLAN_DAY_OF_WEEK_RULES.DAY_OF_WEEK.asc())
            .fetch { ruleRecord ->
                DayOfWeekRuleRow(
                    dayOfWeek = DayOfWeek.valueOf(ruleRecord.get(RATE_PLAN_DAY_OF_WEEK_RULES.DAY_OF_WEEK)),
                    priceAmount = ruleRecord.get(RATE_PLAN_DAY_OF_WEEK_RULES.PRICE_AMOUNT)
                )
            }

        return RatePlan.reconstitute(
            id = record.get(RATE_PLANS.ID),
            propertyId = record.get(RATE_PLANS.PROPERTY_ID),
            roomTypeId = record.get(RATE_PLANS.ROOM_TYPE_ID),
            name = record.get(RATE_PLANS.NAME),
            type = com.stayops.rate.domain.model.RatePlanType.valueOf(record.get(RATE_PLANS.TYPE)),
            dateRange = if (record.get(RATE_PLANS.DATE_RANGE_START) != null && record.get(RATE_PLANS.DATE_RANGE_END) != null) {
                DateRange.of(record.get(RATE_PLANS.DATE_RANGE_START), record.get(RATE_PLANS.DATE_RANGE_END))
            } else null,
            dayOfWeekRules = rules
                .groupBy { row -> row.priceAmount }
                .map { entry ->
                    DayOfWeekRate(
                        daysOfWeek = entry.value.map { row -> row.dayOfWeek }.toSet(),
                        price = Money.of(entry.key)
                    )
                }
                .ifEmpty { null },
            channelCode = record.get(RATE_PLANS.CHANNEL_CODE),
            price = Money.of(record.get(RATE_PLANS.PRICE_AMOUNT)),
            priority = record.get(RATE_PLANS.PRIORITY),
            status = RatePlanStatus.valueOf(record.get(RATE_PLANS.STATUS)),
            version = record.get(RATE_PLANS.VERSION),
            createdAt = record.get(RATE_PLANS.CREATED_AT).toInstant(),
            updatedAt = record.get(RATE_PLANS.UPDATED_AT).toInstant()
        )
    }

    private data class DayOfWeekRuleRow(
        val dayOfWeek: DayOfWeek,
        val priceAmount: Long
    )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
