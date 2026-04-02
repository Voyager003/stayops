package com.stayops.channel.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.reservation.domain.event.ReservationCancelled
import com.stayops.reservation.domain.event.ReservationCreated
import com.stayops.shared.domain.DateRange
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.*
import java.time.LocalDate

class ChannelEventHandlerTest : BehaviorSpec({

    val channelSyncApplication = mockk<ChannelSyncApplication>()
    val inventoryRepository = mockk<RoomInventoryRepository>()

    val sut = ChannelEventHandler(
        channelSyncApplication = channelSyncApplication,
        inventoryRepository = inventoryRepository
    )

    val checkIn = LocalDate.of(2026, 4, 1)
    val checkOut = LocalDate.of(2026, 4, 3)

    fun inventoryWithAvailable(date: LocalDate, available: Int) = RoomInventory.create(
        id = "inv-$date", propertyId = "prop-1", roomTypeId = "rt-1",
        date = date, totalCount = 10
    )

    given("ReservationCreated 이벤트 수신 시") {
        `when`("2박 예약이 생성되면") {
            then("날짜별로 ARI 동기화 태스크가 생성된다") {
                clearAllMocks()
                every { inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", checkIn) } returns
                        inventoryWithAvailable(checkIn, 9)
                every { inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", checkIn.plusDays(1)) } returns
                        inventoryWithAvailable(checkIn.plusDays(1), 8)
                justRun { channelSyncApplication.createAvailabilitySyncTasks(any(), any(), any(), any()) }
                justRun { channelSyncApplication.processTasksImmediately(any()) }

                sut.onReservationCreated(
                    ReservationCreated(
                        reservationId = "rsv-1",
                        propertyId = "prop-1",
                        roomTypeId = "rt-1",
                        dateRange = DateRange.of(checkIn, checkOut),
                        channelCode = "DIRECT"
                    )
                )

                verify(exactly = 2) { channelSyncApplication.createAvailabilitySyncTasks(any(), any(), any(), any()) }
            }
        }
    }

    given("ReservationCancelled 이벤트 수신 시") {
        `when`("예약이 취소되면") {
            then("날짜별로 ARI 동기화 태스크가 생성된다") {
                clearAllMocks()
                every { inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", any()) } returns
                        inventoryWithAvailable(checkIn, 10)
                justRun { channelSyncApplication.createAvailabilitySyncTasks(any(), any(), any(), any()) }
                justRun { channelSyncApplication.processTasksImmediately(any()) }

                sut.onReservationCancelled(
                    ReservationCancelled(
                        reservationId = "rsv-2",
                        propertyId = "prop-1",
                        roomTypeId = "rt-1",
                        dateRange = DateRange.of(checkIn, checkOut)
                    )
                )

                verify(exactly = 2) { channelSyncApplication.createAvailabilitySyncTasks(any(), any(), any(), any()) }
            }
        }
    }
})
