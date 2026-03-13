package com.stayops.inventory.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.inventory.infrastructure.cache.RedisRoomInventoryCache
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class RoomInventoryApplication(
    private val inventoryRepository: RoomInventoryRepository,
    private val cache: RedisRoomInventoryCache
) {
    fun initializeInventory(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        totalCount: Int
    ): RoomInventory {
        inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate(propertyId, roomTypeId, date)?.let {
            throw ConflictException("INVENTORY_ALREADY_EXISTS", "해당 날짜의 재고가 이미 존재합니다: $date")
        }
        val inventory = RoomInventory.create(
            id = UUID.randomUUID().toString(),
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            date = date,
            totalCount = totalCount
        )
        return inventoryRepository.save(inventory).also { cache.put(it) }
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

    // Used internally by Reservation domain
    fun reserve(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory {
        val inventory = getOrThrow(propertyId, roomTypeId, date)
        return try {
            saveAndEvict(inventory.reserve())
        } catch (e: OptimisticLockingFailureException) {
            throw ConflictException("INVENTORY_CONFLICT", "재고 변경 충돌이 발생했습니다. 다시 시도해주세요.")
        }
    }

    // Used internally by Reservation domain
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
