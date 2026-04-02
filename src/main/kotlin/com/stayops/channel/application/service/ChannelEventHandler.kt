package com.stayops.channel.application.service

import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.reservation.domain.event.ReservationCancelled
import com.stayops.reservation.domain.event.ReservationCreated
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ChannelEventHandler(
    private val channelSyncApplication: ChannelSyncApplication,
    private val inventoryRepository: RoomInventoryRepository
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onReservationCreated(event: ReservationCreated) {
        event.dateRange.allDates().forEach { date ->
            val inventory = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate(
                event.propertyId, event.roomTypeId, date
            )
            if (inventory != null) {
                channelSyncApplication.createAvailabilitySyncTasks(
                    propertyId = event.propertyId,
                    roomTypeId = event.roomTypeId,
                    date = date,
                    availableCount = inventory.availableCount
                )
            }
        }
        log.info("예약 생성 → ARI 동기화 태스크 생성: reservationId={}", event.reservationId)
        channelSyncApplication.processTasksImmediately(event.propertyId)
    }

    @EventListener
    fun onReservationCancelled(event: ReservationCancelled) {
        event.dateRange.allDates().forEach { date ->
            val inventory = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate(
                event.propertyId, event.roomTypeId, date
            )
            if (inventory != null) {
                channelSyncApplication.createAvailabilitySyncTasks(
                    propertyId = event.propertyId,
                    roomTypeId = event.roomTypeId,
                    date = date,
                    availableCount = inventory.availableCount
                )
            }
        }
        log.info("예약 취소 → ARI 동기화 태스크 생성: reservationId={}", event.reservationId)
        channelSyncApplication.processTasksImmediately(event.propertyId)
    }
}
