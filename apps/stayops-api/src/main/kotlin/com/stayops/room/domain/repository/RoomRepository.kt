package com.stayops.room.domain.repository

import com.stayops.room.domain.model.Room

interface RoomRepository {
    fun save(room: Room): Room
    fun findById(id: String): Room?
    fun findByPropertyId(propertyId: String): List<Room>
    fun findByRoomTypeId(roomTypeId: String): List<Room>
    fun findByPropertyIdAndRoomNumber(propertyId: String, roomNumber: String): Room?
}
