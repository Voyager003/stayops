package com.stayops.room.application.service

import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.room.api.dto.RoomStatusAction
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RoomApplication(
    private val roomRepository: RoomRepository,
    private val inventoryApplication: RoomInventoryApplication
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createRoom(
        propertyId: String,
        roomTypeId: String,
        roomNumber: String,
        floor: Int
    ): Room {
        roomRepository.findByPropertyIdAndRoomNumber(propertyId, roomNumber)?.let {
            throw ConflictException("ROOM_NUMBER_DUPLICATE", "해당 숙소에 이미 동일한 호수의 객실이 존재합니다: $roomNumber")
        }
        val room = Room.create(
            id = UUID.randomUUID().toString(),
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            roomNumber = roomNumber,
            floor = floor
        )
        val saved = roomRepository.save(room)
        inventoryApplication.syncInventoryForRoomType(propertyId, roomTypeId)
        log.info("객실 생성: roomId={}, propertyId={}, roomNumber={}", saved.id, propertyId, roomNumber)
        return saved
    }

    fun getRoom(id: String): Room =
        roomRepository.findById(id)
            ?: throw NotFoundException("ROOM_NOT_FOUND", "객실을 찾을 수 없습니다: $id")

    fun getRoomsByProperty(propertyId: String): List<Room> =
        roomRepository.findByPropertyId(propertyId)

    fun updateMemo(id: String, memo: String?): Room {
        val room = getRoom(id)
        val saved = roomRepository.save(room.updateMemo(memo))
        log.info("객실 메모 수정: roomId={}", id)
        return saved
    }

    fun changeStatus(id: String, action: RoomStatusAction): Room {
        val room = getRoom(id)
        val updated = when (action) {
            RoomStatusAction.CHECK_IN -> room.checkIn()
            RoomStatusAction.CHECK_OUT -> room.checkOut()
            RoomStatusAction.COMPLETE_CLEANING -> room.completeCleaning()
            RoomStatusAction.START_MAINTENANCE -> room.startMaintenance()
            RoomStatusAction.COMPLETE_MAINTENANCE -> room.completeMaintenance()
        }
        val saved = roomRepository.save(updated)
        log.info("객실 상태 변경: roomId={}, action={}", id, action)
        return saved
    }
}
