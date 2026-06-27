package com.stayops.inventory.infrastructure.persistence.rdb

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.jooq.generated.Tables.ROOM_INVENTORIES
import com.stayops.jooq.generated.tables.records.RoomInventoriesRecord
import com.stayops.shared.config.RdbPersistence
import com.stayops.shared.exception.ConflictException
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbRoomInventoryRepository(
    private val dsl: DSLContext
) : RoomInventoryRepository {

    override fun save(inventory: RoomInventory): RoomInventory =
        dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)

            val updatedRows = if (inventory.version == null) {
                tx.insertInto(ROOM_INVENTORIES)
                    .set(ROOM_INVENTORIES.ID, inventory.id)
                    .set(ROOM_INVENTORIES.PROPERTY_ID, inventory.propertyId)
                    .set(ROOM_INVENTORIES.ROOM_TYPE_ID, inventory.roomTypeId)
                    .set(ROOM_INVENTORIES.INVENTORY_DATE, inventory.date)
                    .set(ROOM_INVENTORIES.TOTAL_COUNT, inventory.totalCount)
                    .set(ROOM_INVENTORIES.RESERVED_COUNT, inventory.reservedCount)
                    .set(ROOM_INVENTORIES.BLOCKED_COUNT, inventory.blockedCount)
                    .set(ROOM_INVENTORIES.HELD_COUNT, inventory.heldCount)
                    .set(ROOM_INVENTORIES.VERSION, 0L)
                    .set(ROOM_INVENTORIES.CREATED_AT, inventory.createdAt.toOffsetDateTime())
                    .set(ROOM_INVENTORIES.UPDATED_AT, inventory.updatedAt.toOffsetDateTime())
                    .execute()
            } else {
                tx.update(ROOM_INVENTORIES)
                    .set(ROOM_INVENTORIES.PROPERTY_ID, inventory.propertyId)
                    .set(ROOM_INVENTORIES.ROOM_TYPE_ID, inventory.roomTypeId)
                    .set(ROOM_INVENTORIES.INVENTORY_DATE, inventory.date)
                    .set(ROOM_INVENTORIES.TOTAL_COUNT, inventory.totalCount)
                    .set(ROOM_INVENTORIES.RESERVED_COUNT, inventory.reservedCount)
                    .set(ROOM_INVENTORIES.BLOCKED_COUNT, inventory.blockedCount)
                    .set(ROOM_INVENTORIES.HELD_COUNT, inventory.heldCount)
                    .set(ROOM_INVENTORIES.VERSION, inventory.version + 1)
                    .set(ROOM_INVENTORIES.CREATED_AT, inventory.createdAt.toOffsetDateTime())
                    .set(ROOM_INVENTORIES.UPDATED_AT, inventory.updatedAt.toOffsetDateTime())
                    .where(ROOM_INVENTORIES.ID.eq(inventory.id))
                    .and(ROOM_INVENTORIES.VERSION.eq(inventory.version))
                    .execute()
            }

            if (updatedRows == 0 && inventory.version != null) {
                throw ConflictException(
                    code = "INVENTORY_CONFLICT",
                    message = "재고 변경 충돌이 발생했습니다. 다시 시도해주세요."
                )
            }

            tx.findInventoryById(inventory.id) ?: inventory
        }

    override fun findByPropertyIdAndRoomTypeIdAndDate(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate
    ): RoomInventory? =
        dsl.selectFrom(ROOM_INVENTORIES)
            .where(ROOM_INVENTORIES.PROPERTY_ID.eq(propertyId))
            .and(ROOM_INVENTORIES.ROOM_TYPE_ID.eq(roomTypeId))
            .and(ROOM_INVENTORIES.INVENTORY_DATE.eq(date))
            .fetchOne()
            ?.toDomain()

    override fun findByPropertyIdAndRoomTypeIdAndDateBetween(
        propertyId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RoomInventory> =
        dsl.selectFrom(ROOM_INVENTORIES)
            .where(ROOM_INVENTORIES.PROPERTY_ID.eq(propertyId))
            .and(ROOM_INVENTORIES.ROOM_TYPE_ID.eq(roomTypeId))
            .and(ROOM_INVENTORIES.INVENTORY_DATE.between(startDate, endDate))
            .orderBy(ROOM_INVENTORIES.INVENTORY_DATE.asc())
            .fetch { it.toDomain() }

    private fun DSLContext.findInventoryById(id: String): RoomInventory? =
        selectFrom(ROOM_INVENTORIES)
            .where(ROOM_INVENTORIES.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    private fun RoomInventoriesRecord.toDomain(): RoomInventory =
        RoomInventory.reconstitute(
            id = get(ROOM_INVENTORIES.ID),
            propertyId = get(ROOM_INVENTORIES.PROPERTY_ID),
            roomTypeId = get(ROOM_INVENTORIES.ROOM_TYPE_ID),
            date = get(ROOM_INVENTORIES.INVENTORY_DATE),
            totalCount = get(ROOM_INVENTORIES.TOTAL_COUNT),
            reservedCount = get(ROOM_INVENTORIES.RESERVED_COUNT),
            blockedCount = get(ROOM_INVENTORIES.BLOCKED_COUNT),
            heldCount = get(ROOM_INVENTORIES.HELD_COUNT),
            version = get(ROOM_INVENTORIES.VERSION),
            createdAt = get(ROOM_INVENTORIES.CREATED_AT).toInstant(),
            updatedAt = get(ROOM_INVENTORIES.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
