package com.stayops.inventory.infrastructure.persistence.rdb

import com.stayops.inventory.domain.model.InventoryHold
import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.jooq.generated.Tables.INVENTORY_HOLD_DATES
import com.stayops.jooq.generated.Tables.INVENTORY_HOLDS
import com.stayops.jooq.generated.tables.records.InventoryHoldsRecord
import com.stayops.shared.config.RdbPersistence
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbInventoryHoldRepository(
    private val dsl: DSLContext
) : InventoryHoldRepository {

    override fun save(hold: InventoryHold): InventoryHold =
        dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)
            tx.insertInto(INVENTORY_HOLDS)
                .set(INVENTORY_HOLDS.ID, hold.id)
                .set(INVENTORY_HOLDS.RESERVATION_INTENT_ID, hold.reservationIntentId)
                .set(INVENTORY_HOLDS.PROPERTY_ID, hold.propertyId)
                .set(INVENTORY_HOLDS.ROOM_TYPE_ID, hold.roomTypeId)
                .set(INVENTORY_HOLDS.QUANTITY, hold.quantity)
                .set(INVENTORY_HOLDS.STATUS, hold.status.name)
                .set(INVENTORY_HOLDS.EXPIRES_AT, hold.expiresAt.toOffsetDateTime())
                .set(INVENTORY_HOLDS.VERSION, hold.version)
                .set(INVENTORY_HOLDS.CREATED_AT, hold.createdAt.toOffsetDateTime())
                .set(INVENTORY_HOLDS.UPDATED_AT, hold.updatedAt.toOffsetDateTime())
                .onConflict(INVENTORY_HOLDS.ID)
                .doUpdate()
                .set(INVENTORY_HOLDS.RESERVATION_INTENT_ID, hold.reservationIntentId)
                .set(INVENTORY_HOLDS.PROPERTY_ID, hold.propertyId)
                .set(INVENTORY_HOLDS.ROOM_TYPE_ID, hold.roomTypeId)
                .set(INVENTORY_HOLDS.QUANTITY, hold.quantity)
                .set(INVENTORY_HOLDS.STATUS, hold.status.name)
                .set(INVENTORY_HOLDS.EXPIRES_AT, hold.expiresAt.toOffsetDateTime())
                .set(INVENTORY_HOLDS.VERSION, hold.version)
                .set(INVENTORY_HOLDS.CREATED_AT, hold.createdAt.toOffsetDateTime())
                .set(INVENTORY_HOLDS.UPDATED_AT, hold.updatedAt.toOffsetDateTime())
                .execute()

            tx.deleteFrom(INVENTORY_HOLD_DATES)
                .where(INVENTORY_HOLD_DATES.HOLD_ID.eq(hold.id))
                .execute()

            hold.dates.forEach { date ->
                tx.insertInto(INVENTORY_HOLD_DATES)
                    .set(INVENTORY_HOLD_DATES.HOLD_ID, hold.id)
                    .set(INVENTORY_HOLD_DATES.HOLD_DATE, date)
                    .set(INVENTORY_HOLD_DATES.QUANTITY, hold.quantity)
                    .execute()
            }

            tx.findById(hold.id) ?: hold
        }

    override fun findById(id: String): InventoryHold? =
        dsl.findById(id)

    override fun findByReservationIntentId(reservationIntentId: String): InventoryHold? {
        val record = dsl.selectFrom(INVENTORY_HOLDS)
            .where(INVENTORY_HOLDS.RESERVATION_INTENT_ID.eq(reservationIntentId))
            .fetchOne()
            ?: return null
        return record.toDomain(loadDates(record.id))
    }

    override fun findActiveByPropertyIdAndRoomTypeIdAndDates(
        propertyId: String,
        roomTypeId: String,
        dates: List<LocalDate>,
        now: Instant
    ): List<InventoryHold> {
        if (dates.isEmpty()) return emptyList()
        return dsl.selectDistinct(INVENTORY_HOLDS.asterisk())
            .from(INVENTORY_HOLDS)
            .join(INVENTORY_HOLD_DATES).on(INVENTORY_HOLD_DATES.HOLD_ID.eq(INVENTORY_HOLDS.ID))
            .where(INVENTORY_HOLDS.PROPERTY_ID.eq(propertyId))
            .and(INVENTORY_HOLDS.ROOM_TYPE_ID.eq(roomTypeId))
            .and(INVENTORY_HOLDS.STATUS.`in`(activeStatuses.map { it.name }))
            .and(INVENTORY_HOLDS.EXPIRES_AT.ge(now.toOffsetDateTime()))
            .and(INVENTORY_HOLD_DATES.HOLD_DATE.`in`(dates))
            .orderBy(INVENTORY_HOLDS.CREATED_AT.asc(), INVENTORY_HOLDS.ID.asc())
            .fetch { row ->
                val record = row.into(INVENTORY_HOLDS)
                record.toDomain(loadDates(record.id))
            }
    }

    private fun DSLContext.findById(id: String): InventoryHold? {
        val record = selectFrom(INVENTORY_HOLDS)
            .where(INVENTORY_HOLDS.ID.eq(id))
            .fetchOne()
            ?: return null
        return record.toDomain(loadDates(id))
    }

    private fun loadDates(holdId: String): List<LocalDate> =
        dsl.select(INVENTORY_HOLD_DATES.HOLD_DATE)
            .from(INVENTORY_HOLD_DATES)
            .where(INVENTORY_HOLD_DATES.HOLD_ID.eq(holdId))
            .orderBy(INVENTORY_HOLD_DATES.HOLD_DATE.asc())
            .fetch { it.value1() }

    private fun InventoryHoldsRecord.toDomain(dates: List<LocalDate>): InventoryHold =
        InventoryHold.reconstitute(
            id = get(INVENTORY_HOLDS.ID),
            reservationIntentId = get(INVENTORY_HOLDS.RESERVATION_INTENT_ID),
            propertyId = get(INVENTORY_HOLDS.PROPERTY_ID),
            roomTypeId = get(INVENTORY_HOLDS.ROOM_TYPE_ID),
            dates = dates,
            quantity = get(INVENTORY_HOLDS.QUANTITY),
            status = InventoryHoldStatus.valueOf(get(INVENTORY_HOLDS.STATUS)),
            expiresAt = get(INVENTORY_HOLDS.EXPIRES_AT).toInstant(),
            version = get(INVENTORY_HOLDS.VERSION),
            createdAt = get(INVENTORY_HOLDS.CREATED_AT).toInstant(),
            updatedAt = get(INVENTORY_HOLDS.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)

    companion object {
        private val activeStatuses = listOf(
            InventoryHoldStatus.HELD,
            InventoryHoldStatus.PAYMENT_PROCESSING
        )
    }
}
