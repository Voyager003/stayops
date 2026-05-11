package com.stayops.room.infrastructure.persistence

import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomStatus
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("rooms")
@CompoundIndex(def = "{'propertyId': 1, 'roomNumber': 1}", unique = true)
data class RoomDocument(
    @Id val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val roomNumber: String,
    val floor: Int,
    val status: RoomStatus,
    val memo: String?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun toDomain(): Room = Room.reconstitute(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        roomNumber = roomNumber,
        floor = floor,
        status = status,
        memo = memo,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(room: Room): RoomDocument = RoomDocument(
            id = room.id,
            propertyId = room.propertyId,
            roomTypeId = room.roomTypeId,
            roomNumber = room.roomNumber,
            floor = room.floor,
            status = room.status,
            memo = room.memo,
            version = room.version,
            createdAt = room.createdAt,
            updatedAt = room.updatedAt
        )
    }
}
