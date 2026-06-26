package com.stayops.room.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.jooq.generated.Tables.ROOM_TYPE_AMENITIES
import com.stayops.jooq.generated.tables.records.RoomTypesRecord
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.config.RdbPersistence
import com.stayops.shared.domain.Money
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbRoomTypeRepository(
    private val dsl: DSLContext
) : RoomTypeRepository {

    override fun save(roomType: RoomType): RoomType =
        dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)

            tx.insertInto(ROOM_TYPES)
                .set(ROOM_TYPES.ID, roomType.id)
                .set(ROOM_TYPES.PROPERTY_ID, roomType.propertyId)
                .set(ROOM_TYPES.NAME, roomType.name)
                .set(ROOM_TYPES.DESCRIPTION, roomType.description)
                .set(ROOM_TYPES.MAX_OCCUPANCY, roomType.maxOccupancy)
                .set(ROOM_TYPES.BASE_PRICE_AMOUNT, roomType.basePrice.amount)
                .set(ROOM_TYPES.BASE_PRICE_CURRENCY, roomType.basePrice.currency)
                .set(ROOM_TYPES.VERSION, roomType.version)
                .set(ROOM_TYPES.CREATED_AT, roomType.createdAt.toOffsetDateTime())
                .set(ROOM_TYPES.UPDATED_AT, roomType.updatedAt.toOffsetDateTime())
                .onConflict(ROOM_TYPES.ID)
                .doUpdate()
                .set(ROOM_TYPES.PROPERTY_ID, roomType.propertyId)
                .set(ROOM_TYPES.NAME, roomType.name)
                .set(ROOM_TYPES.DESCRIPTION, roomType.description)
                .set(ROOM_TYPES.MAX_OCCUPANCY, roomType.maxOccupancy)
                .set(ROOM_TYPES.BASE_PRICE_AMOUNT, roomType.basePrice.amount)
                .set(ROOM_TYPES.BASE_PRICE_CURRENCY, roomType.basePrice.currency)
                .set(ROOM_TYPES.VERSION, roomType.version)
                .set(ROOM_TYPES.CREATED_AT, roomType.createdAt.toOffsetDateTime())
                .set(ROOM_TYPES.UPDATED_AT, roomType.updatedAt.toOffsetDateTime())
                .execute()

            tx.deleteFrom(ROOM_TYPE_AMENITIES)
                .where(ROOM_TYPE_AMENITIES.ROOM_TYPE_ID.eq(roomType.id))
                .execute()

            roomType.amenities.forEach { amenity ->
                tx.insertInto(ROOM_TYPE_AMENITIES)
                    .set(ROOM_TYPE_AMENITIES.ROOM_TYPE_ID, roomType.id)
                    .set(ROOM_TYPE_AMENITIES.AMENITY, amenity)
                    .execute()
            }

            tx.findRoomTypeById(roomType.id) ?: roomType
        }

    override fun findById(id: String): RoomType? =
        dsl.findRoomTypeById(id)

    override fun findByPropertyId(propertyId: String): List<RoomType> =
        dsl.selectFrom(ROOM_TYPES)
            .where(ROOM_TYPES.PROPERTY_ID.eq(propertyId))
            .orderBy(ROOM_TYPES.ID.asc())
            .fetch { dsl.toDomain(it) }

    override fun findByPropertyIdAndName(propertyId: String, name: String): RoomType? =
        dsl.selectFrom(ROOM_TYPES)
            .where(ROOM_TYPES.PROPERTY_ID.eq(propertyId))
            .and(ROOM_TYPES.NAME.eq(name))
            .fetchOne()
            ?.let { dsl.toDomain(it) }

    override fun deleteById(id: String) {
        dsl.transaction { configuration ->
            val tx = DSL.using(configuration)
            tx.deleteFrom(ROOM_TYPE_AMENITIES)
                .where(ROOM_TYPE_AMENITIES.ROOM_TYPE_ID.eq(id))
                .execute()
            tx.deleteFrom(ROOM_TYPES)
                .where(ROOM_TYPES.ID.eq(id))
                .execute()
        }
    }

    private fun DSLContext.findRoomTypeById(id: String): RoomType? =
        selectFrom(ROOM_TYPES)
            .where(ROOM_TYPES.ID.eq(id))
            .fetchOne()
            ?.let { toDomain(it) }

    private fun DSLContext.toDomain(record: RoomTypesRecord): RoomType {
        val amenities = select(ROOM_TYPE_AMENITIES.AMENITY)
            .from(ROOM_TYPE_AMENITIES)
            .where(ROOM_TYPE_AMENITIES.ROOM_TYPE_ID.eq(record.get(ROOM_TYPES.ID)))
            .orderBy(ROOM_TYPE_AMENITIES.AMENITY.asc())
            .fetch { it.get(ROOM_TYPE_AMENITIES.AMENITY) }

        return RoomType.reconstitute(
            id = record.get(ROOM_TYPES.ID),
            propertyId = record.get(ROOM_TYPES.PROPERTY_ID),
            name = record.get(ROOM_TYPES.NAME),
            description = record.get(ROOM_TYPES.DESCRIPTION),
            maxOccupancy = record.get(ROOM_TYPES.MAX_OCCUPANCY),
            basePrice = Money.of(
                amount = record.get(ROOM_TYPES.BASE_PRICE_AMOUNT),
                currency = record.get(ROOM_TYPES.BASE_PRICE_CURRENCY)
            ),
            amenities = amenities,
            version = record.get(ROOM_TYPES.VERSION),
            createdAt = record.get(ROOM_TYPES.CREATED_AT).toInstant(),
            updatedAt = record.get(ROOM_TYPES.UPDATED_AT).toInstant()
        )
    }

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
