package com.stayops.inventory.infrastructure.scheduler

import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.property.domain.model.PropertyStatus
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.scheduler.SchedulerLock
import org.springframework.beans.factory.annotation.Value
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Component
class InventoryRollingScheduler(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val schedulerLock: SchedulerLock,
    private val clock: Clock,
    @Value("\${stayops.instance-id:\${HOSTNAME:local}}")
    private val instanceId: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 2 * * *")
    fun syncAllInventories() {
        val lockName = "inventory-rolling"
        val owner = "$lockName-$instanceId"
        val now = clock.instant()
        val lockedUntil = now.plus(Duration.ofHours(2))

        if (!schedulerLock.tryAcquire(lockName, owner, lockedUntil, now)) {
            log.info("재고 rolling 동기화 건너뜀: 다른 인스턴스가 실행 중입니다. owner={}", owner)
            return
        }

        try {
            syncAllInventoriesWithLock()
        } finally {
            schedulerLock.release(lockName, owner)
        }
    }

    private fun syncAllInventoriesWithLock() {
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
