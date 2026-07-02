package com.stayops.room.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.ROOMS
import com.stayops.jooq.generated.tables.records.RoomsRecord
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomStatus
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.config.RdbPersistence
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbRoomRepository(
    private val dsl: DSLContext
) : RoomRepository {

    override fun save(room: Room): Room {
        dsl.insertInto(ROOMS)
            .set(ROOMS.ID, room.id)
            .set(ROOMS.PROPERTY_ID, room.propertyId)
            .set(ROOMS.ROOM_TYPE_ID, room.roomTypeId)
            .set(ROOMS.ROOM_NUMBER, room.roomNumber)
            .set(ROOMS.FLOOR, room.floor)
            .set(ROOMS.STATUS, room.status.name)
            .set(ROOMS.MEMO, room.memo)
            .set(ROOMS.VERSION, room.version)
            .set(ROOMS.CREATED_AT, room.createdAt.toOffsetDateTime())
            .set(ROOMS.UPDATED_AT, room.updatedAt.toOffsetDateTime())
            .onConflict(ROOMS.ID)
            .doUpdate()
            .set(ROOMS.PROPERTY_ID, room.propertyId)
            .set(ROOMS.ROOM_TYPE_ID, room.roomTypeId)
            .set(ROOMS.ROOM_NUMBER, room.roomNumber)
            .set(ROOMS.FLOOR, room.floor)
            .set(ROOMS.STATUS, room.status.name)
            .set(ROOMS.MEMO, room.memo)
            .set(ROOMS.VERSION, room.version)
            .set(ROOMS.CREATED_AT, room.createdAt.toOffsetDateTime())
            .set(ROOMS.UPDATED_AT, room.updatedAt.toOffsetDateTime())
            .execute()

        return findById(room.id) ?: room
    }

    override fun findById(id: String): Room? =
        dsl.selectFrom(ROOMS)
            .where(ROOMS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    override fun findByPropertyId(propertyId: String): List<Room> =
        dsl.selectFrom(ROOMS)
            .where(ROOMS.PROPERTY_ID.eq(propertyId))
            .orderBy(ROOMS.ID.asc())
            .fetch { it.toDomain() }

    override fun findByRoomTypeId(roomTypeId: String): List<Room> =
        dsl.selectFrom(ROOMS)
            .where(ROOMS.ROOM_TYPE_ID.eq(roomTypeId))
            .orderBy(ROOMS.ID.asc())
            .fetch { it.toDomain() }

    override fun findByPropertyIdAndRoomNumber(propertyId: String, roomNumber: String): Room? =
        dsl.selectFrom(ROOMS)
            .where(ROOMS.PROPERTY_ID.eq(propertyId))
            .and(ROOMS.ROOM_NUMBER.eq(roomNumber))
            .fetchOne()
            ?.toDomain()

    private fun RoomsRecord.toDomain(): Room =
        Room.reconstitute(
            id = get(ROOMS.ID),
            propertyId = get(ROOMS.PROPERTY_ID),
            roomTypeId = get(ROOMS.ROOM_TYPE_ID),
            roomNumber = get(ROOMS.ROOM_NUMBER),
            floor = get(ROOMS.FLOOR),
            status = RoomStatus.valueOf(get(ROOMS.STATUS)),
            memo = get(ROOMS.MEMO),
            version = get(ROOMS.VERSION),
            createdAt = get(ROOMS.CREATED_AT).toInstant(),
            updatedAt = get(ROOMS.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
