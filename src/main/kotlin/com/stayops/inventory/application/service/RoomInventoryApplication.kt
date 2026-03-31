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

    companion object {
        const val INVENTORY_HORIZON_DAYS = 90L
    }

    fun syncInventoryForRoomType(propertyId: String, roomTypeId: String) {
        val roomCount = roomRepository.findByRoomTypeId(roomTypeId)
            .count { it.propertyId == propertyId }
        if (roomCount < 1) return

        val today = LocalDate.now()
        val endDate = today.plusDays(INVENTORY_HORIZON_DAYS)
        val existing = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
            propertyId, roomTypeId, today, endDate
        ).associateBy { it.date }

        var date = today
        while (!date.isAfter(endDate)) {
            val inv = existing[date]
            if (inv == null) {
                inventoryRepository.save(
                    RoomInventory.create(
                        id = UUID.randomUUID().toString(),
                        propertyId = propertyId,
                        roomTypeId = roomTypeId,
                        date = date,
                        totalCount = roomCount
                    )
                )
            } else if (inv.totalCount != roomCount) {
                inventoryRepository.save(inv.updateTotalCount(roomCount))
            }
            date = date.plusDays(1)
        }
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
