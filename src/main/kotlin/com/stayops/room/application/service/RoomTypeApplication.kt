package com.stayops.room.application.service

import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class RoomTypeApplication(
    private val roomTypeRepository: RoomTypeRepository
) {
    fun createRoomType(
        propertyId: String,
        name: String,
        description: String,
        maxOccupancy: Int,
        basePrice: BigDecimal,
        currency: String = "KRW",
        amenities: List<String> = emptyList()
    ): RoomType {
        roomTypeRepository.findByPropertyIdAndName(propertyId, name)?.let {
            throw ConflictException("ROOM_TYPE_NAME_DUPLICATE", "해당 숙소에 이미 동일한 이름의 객실타입이 존재합니다: $name")
        }
        val roomType = RoomType.create(
            id = UUID.randomUUID().toString(),
            propertyId = propertyId,
            name = name,
            description = description,
            maxOccupancy = maxOccupancy,
            basePrice = Money.of(basePrice, currency),
            amenities = amenities
        )
        return roomTypeRepository.save(roomType)
    }

    fun getRoomType(id: String): RoomType =
        roomTypeRepository.findById(id)
            ?: throw NotFoundException("ROOM_TYPE_NOT_FOUND", "객실타입을 찾을 수 없습니다: $id")

    fun getRoomTypesByProperty(propertyId: String): List<RoomType> =
        roomTypeRepository.findByPropertyId(propertyId)

    fun updateRoomType(
        id: String,
        name: String,
        description: String,
        maxOccupancy: Int,
        basePrice: BigDecimal,
        currency: String = "KRW",
        amenities: List<String> = emptyList()
    ): RoomType {
        val roomType = getRoomType(id)
        if (roomType.name != name) {
            roomTypeRepository.findByPropertyIdAndName(roomType.propertyId, name)?.let {
                throw ConflictException("ROOM_TYPE_NAME_DUPLICATE", "해당 숙소에 이미 동일한 이름의 객실타입이 존재합니다: $name")
            }
        }
        val updated = roomType.updateInfo(
            name = name,
            description = description,
            maxOccupancy = maxOccupancy,
            basePrice = Money.of(basePrice, currency),
            amenities = amenities
        )
        return roomTypeRepository.save(updated)
    }

    fun deactivateRoomType(id: String) {
        val roomType = getRoomType(id)
        roomTypeRepository.save(roomType.deactivate())
    }
}
