package com.stayops.inventory.infrastructure.scheduler

import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.property.domain.model.PropertyStatus
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class InventoryRollingScheduler(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val inventoryApplication: RoomInventoryApplication
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 2 * * *")
    fun syncAllInventories() {
        val properties = propertyRepository.findAll().filter { it.status == PropertyStatus.ACTIVE }
        log.info("재고 rolling 동기화 시작: 활성 숙소 ${properties.size}개")

        for (property in properties) {
            val roomTypes = roomTypeRepository.findByPropertyId(property.id)
            for (roomType in roomTypes) {
                inventoryApplication.syncInventoryForRoomType(property.id, roomType.id)
            }
        }

        log.info("재고 rolling 동기화 완료")
    }
}
