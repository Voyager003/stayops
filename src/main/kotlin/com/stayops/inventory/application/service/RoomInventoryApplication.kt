package com.stayops.inventory.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.inventory.infrastructure.cache.RedisRoomInventoryCache
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class RoomInventoryApplication(
    private val inventoryRepository: RoomInventoryRepository,
    private val cache: RedisRoomInventoryCache,
    private val roomRepository: RoomRepository
) {

    fun openInventory(
        propertyId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int {
        require(!startDate.isAfter(endDate)) { "시작일이 종료일보다 클 수 없습니다." }

        val totalCount = roomRepository.findByRoomTypeId(roomTypeId)
            .count { it.propertyId == propertyId }
        require(totalCount >= 1) { "해당 객실 타입에 등록된 객실이 없습니다." }

        val existing = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
            propertyId, roomTypeId, startDate, endDate
        ).associateBy { it.date }

        var created = 0
        var date = startDate
        while (!date.isAfter(endDate)) {
            if (existing[date] == null) {
                inventoryRepository.save(
                    RoomInventory.create(
                        id = UUID.randomUUID().toString(),
                        propertyId = propertyId,
                        roomTypeId = roomTypeId,
                        date = date,
                        totalCount = totalCount
                    )
                )
                created++
            }
            date = date.plusDays(1)
        }

        return created
    }

    fun getAvailability(
        propertyId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<RoomInventory> =
        inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(propertyId, roomTypeId, startDate, endDate)

    fun blockInventory(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        count: Int
    ): RoomInventory {
        val inventory = getOrThrow(propertyId, roomTypeId, date)
        return try {
            saveAndEvict(inventory.block(count))
        } catch (e: OptimisticLockingFailureException) {
            throw ConflictException("INVENTORY_CONFLICT", "재고 변경 충돌이 발생했습니다. 다시 시도해주세요.")
        }
    }

    fun unblockInventory(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        count: Int
    ): RoomInventory {
        val inventory = getOrThrow(propertyId, roomTypeId, date)
        return try {
            saveAndEvict(inventory.unblock(count))
        } catch (e: OptimisticLockingFailureException) {
            throw ConflictException("INVENTORY_CONFLICT", "재고 변경 충돌이 발생했습니다. 다시 시도해주세요.")
        }
    }

    fun reserve(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory {
        val inventory = getOrThrow(propertyId, roomTypeId, date)
        return try {
            saveAndEvict(inventory.reserve())
        } catch (e: OptimisticLockingFailureException) {
            throw ConflictException("INVENTORY_CONFLICT", "재고 변경 충돌이 발생했습니다. 다시 시도해주세요.")
        }
    }

    fun release(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory {
        val inventory = getOrThrow(propertyId, roomTypeId, date)
        return try {
            saveAndEvict(inventory.release())
        } catch (e: OptimisticLockingFailureException) {
            throw ConflictException("INVENTORY_CONFLICT", "재고 변경 충돌이 발생했습니다. 다시 시도해주세요.")
        }
    }

    private fun getOrThrow(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory =
        cache.get(propertyId, roomTypeId, date)
            ?: inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate(propertyId, roomTypeId, date)
                ?.also { cache.put(it) }
            ?: throw NotFoundException("INVENTORY_NOT_FOUND", "재고를 찾을 수 없습니다: $propertyId/$roomTypeId/$date")

    private fun saveAndEvict(inventory: RoomInventory): RoomInventory =
        inventoryRepository.save(inventory).also {
            cache.evict(inventory.propertyId, inventory.roomTypeId, inventory.date)
        }
}
