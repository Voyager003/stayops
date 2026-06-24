package com.stayops.inventory.application.service

import com.stayops.inventory.application.required.AvailabilitySyncRequester
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.shared.exception.ConflictException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate

@Service
class RoomInventoryManagementApplication(
    private val inventoryRepository: RoomInventoryRepository,
    private val inventoryAccess: RoomInventoryAccessApplication,
    private val availabilitySyncPort: AvailabilitySyncRequester
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun bulkBlock(
        propertyId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        daysOfWeek: List<DayOfWeek>?,
        action: String,
        count: Int
    ): Int {
        require(!startDate.isAfter(endDate)) { "시작일이 종료일보다 클 수 없습니다." }

        var processed = 0
        var date = startDate
        while (!date.isAfter(endDate)) {
            if (daysOfWeek == null || date.dayOfWeek in daysOfWeek) {
                val inventory = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate(propertyId, roomTypeId, date)
                if (inventory != null) {
                    try {
                        val updated = if (action == "BLOCK") inventory.block(count) else inventory.unblock(count)
                        if (updated !== inventory) {
                            inventoryAccess.saveAndEvict(updated)
                            availabilitySyncPort.requestAvailabilitySync(
                                propertyId, roomTypeId, date, updated.availableCount
                            )
                            processed++
                        }
                    } catch (_: IllegalArgumentException) {
                        // count < 1 등은 해당 날짜만 건너뛴다.
                    } catch (_: ConflictException) {
                        // 버전 충돌은 해당 날짜만 건너뛴다.
                    }
                }
            }
            date = date.plusDays(1)
        }
        log.info("재고 일괄 {}: propertyId={}, roomTypeId={}, range={}~{}, processed={}", action, propertyId, roomTypeId, startDate, endDate, processed)
        return processed
    }

    fun blockInventory(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        count: Int
    ): RoomInventory {
        val inventory = inventoryAccess.getOrThrow(propertyId, roomTypeId, date)
        val updated = inventoryAccess.saveAndEvict(inventory.block(count))
        availabilitySyncPort.requestAvailabilitySync(propertyId, roomTypeId, date, updated.availableCount)
        log.info("재고 차단: propertyId={}, roomTypeId={}, date={}, count={}", propertyId, roomTypeId, date, count)
        return updated
    }

    fun unblockInventory(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        count: Int
    ): RoomInventory {
        val inventory = inventoryAccess.getOrThrow(propertyId, roomTypeId, date)
        val updated = inventoryAccess.saveAndEvict(inventory.unblock(count))
        availabilitySyncPort.requestAvailabilitySync(propertyId, roomTypeId, date, updated.availableCount)
        log.info("재고 차단 해제: propertyId={}, roomTypeId={}, date={}, count={}", propertyId, roomTypeId, date, count)
        return updated
    }
}
