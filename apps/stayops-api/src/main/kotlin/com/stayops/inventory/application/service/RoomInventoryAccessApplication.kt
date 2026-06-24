package com.stayops.inventory.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.application.required.RoomInventoryCache
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RoomInventoryAccessApplication(
    private val inventoryRepository: RoomInventoryRepository,
    private val cache: RoomInventoryCache
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getOrThrow(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory =
        cacheGet(propertyId, roomTypeId, date)
            ?: inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate(propertyId, roomTypeId, date)
                ?.also { cachePut(it) }
            ?: throw NotFoundException("INVENTORY_NOT_FOUND", "재고를 찾을 수 없습니다: $propertyId/$roomTypeId/$date")

    fun saveAndEvict(inventory: RoomInventory): RoomInventory =
        inventoryRepository.save(inventory).also {
            cacheEvict(inventory.propertyId, inventory.roomTypeId, inventory.date)
        }

    private fun cacheGet(propertyId: String, roomTypeId: String, date: LocalDate): RoomInventory? =
        runCatching { cache.get(propertyId, roomTypeId, date) }
            .onFailure {
                log.warn(
                    "재고 캐시 조회 실패, DB 조회로 대체: propertyId={}, roomTypeId={}, date={}",
                    propertyId,
                    roomTypeId,
                    date,
                    it
                )
            }
            .getOrNull()

    private fun cachePut(inventory: RoomInventory) {
        runCatching { cache.put(inventory) }
            .onFailure {
                log.warn(
                    "재고 캐시 저장 실패: propertyId={}, roomTypeId={}, date={}",
                    inventory.propertyId,
                    inventory.roomTypeId,
                    inventory.date,
                    it
                )
            }
    }

    private fun cacheEvict(propertyId: String, roomTypeId: String, date: LocalDate) {
        runCatching { cache.evict(propertyId, roomTypeId, date) }
            .onFailure {
                log.warn(
                    "재고 캐시 무효화 실패: propertyId={}, roomTypeId={}, date={}",
                    propertyId,
                    roomTypeId,
                    date,
                    it
                )
            }
    }
}
