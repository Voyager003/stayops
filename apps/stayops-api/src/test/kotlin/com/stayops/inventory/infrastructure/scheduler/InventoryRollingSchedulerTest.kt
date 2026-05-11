package com.stayops.inventory.infrastructure.scheduler

import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import com.stayops.shared.scheduler.SchedulerLock
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class InventoryRollingSchedulerTest : BehaviorSpec({

    val propertyRepository = mockk<PropertyRepository>()
    val roomTypeRepository = mockk<RoomTypeRepository>()
    val inventoryApplication = mockk<RoomInventoryApplication>()
    val schedulerLock = mockk<SchedulerLock>()
    val clock = Clock.fixed(Instant.parse("2026-04-08T02:00:00Z"), ZoneId.of("Asia/Seoul"))

    val scheduler = InventoryRollingScheduler(
        propertyRepository = propertyRepository,
        roomTypeRepository = roomTypeRepository,
        inventoryApplication = inventoryApplication,
        schedulerLock = schedulerLock,
        clock = clock,
        instanceId = "app-1"
    )

    fun property() = Property.create(
        id = "property-1",
        ownerId = "owner-1",
        name = "StayOps Hotel",
        type = com.stayops.property.domain.model.PropertyType.HOTEL,
        address = Address.of("street", "Seoul", "Seoul", "04524", "KR", null, null),
        contactInfo = ContactInfo.of("02-0000-0000", "hotel@example.com", null),
        description = "hotel",
        timezone = "Asia/Seoul",
        currency = "KRW"
    ).activate()

    fun roomType() = RoomType.create(
        id = "room-type-1",
        propertyId = "property-1",
        name = "Deluxe",
        description = "Deluxe room",
        maxOccupancy = 2,
        basePrice = Money.of(BigDecimal("120000"), "KRW")
    )

    given("다른 인스턴스가 rolling scheduler lock을 보유 중이면") {
        then("재고 동기화를 실행하지 않는다") {
            clearAllMocks()
            every { schedulerLock.tryAcquire(any(), any(), any(), any()) } returns false

            scheduler.syncAllInventories()

            verify(exactly = 0) { propertyRepository.findAll() }
            verify(exactly = 0) { inventoryApplication.syncInventoryForRoomType(any(), any()) }
        }
    }

    given("현재 인스턴스가 rolling scheduler lock을 획득하면") {
        then("재고 동기화를 실행하고 lock을 해제한다") {
            clearAllMocks()
            every { schedulerLock.tryAcquire(any(), "inventory-rolling-app-1", any(), clock.instant()) } returns true
            every { propertyRepository.findAll() } returns listOf(property())
            every { roomTypeRepository.findByPropertyId("property-1") } returns listOf(roomType())
            every { inventoryApplication.syncInventoryForRoomType("property-1", "room-type-1") } returns Unit
            every { schedulerLock.release("inventory-rolling", "inventory-rolling-app-1") } returns Unit

            scheduler.syncAllInventories()

            verify(exactly = 1) { inventoryApplication.syncInventoryForRoomType("property-1", "room-type-1") }
            verify(exactly = 1) { schedulerLock.release("inventory-rolling", "inventory-rolling-app-1") }
        }
    }
})
