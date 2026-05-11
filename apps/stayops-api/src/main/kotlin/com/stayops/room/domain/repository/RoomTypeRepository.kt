package com.stayops.room.domain.repository

import com.stayops.room.domain.model.RoomType

interface RoomTypeRepository {
    fun save(roomType: RoomType): RoomType
    fun findById(id: String): RoomType?
    fun findByPropertyId(propertyId: String): List<RoomType>
    fun findByPropertyIdAndName(propertyId: String, name: String): RoomType?
    fun deleteById(id: String)
}
