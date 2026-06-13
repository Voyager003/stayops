package com.stayops.inventory.application.service

import com.stayops.inventory.application.port.InventoryReservationPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RoomInventoryReservationApplication(
    private val inventoryAccess: RoomInventoryAccessApplication
) : InventoryReservationPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun reserve(propertyId: String, roomTypeId: String, date: LocalDate) {
        val inventory = inventoryAccess.getOrThrow(propertyId, roomTypeId, date)
        inventoryAccess.saveAndEvict(inventory.reserve())
        log.info("재고 예약: propertyId={}, roomTypeId={}, date={}", propertyId, roomTypeId, date)
    }

    override fun release(propertyId: String, roomTypeId: String, date: LocalDate) {
        val inventory = inventoryAccess.getOrThrow(propertyId, roomTypeId, date)
        inventoryAccess.saveAndEvict(inventory.release())
        log.info("재고 해제: propertyId={}, roomTypeId={}, date={}", propertyId, roomTypeId, date)
    }
}
