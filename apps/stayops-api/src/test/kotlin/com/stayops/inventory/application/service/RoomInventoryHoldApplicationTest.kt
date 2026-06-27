package com.stayops.inventory.application.service

import com.stayops.inventory.domain.model.InventoryHold
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RoomInventoryHoldApplicationTest : BehaviorSpec({

    val inventoryAccess = mockk<RoomInventoryAccessApplication>()
    val inventoryHoldRepository = mockk<InventoryHoldRepository>()
    val fixedInstant = Instant.parse("2026-04-08T10:00:00Z")
    val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    val idGenerator = object : IdGenerator {
        override fun generate(): String = "hold-1"
    }
    val sut = RoomInventoryHoldApplication(
        inventoryAccess = inventoryAccess,
        inventoryHoldRepository = inventoryHoldRepository,
        idGenerator = idGenerator,
        clock = clock
    )

    fun inventory(date: LocalDate): RoomInventory =
        RoomInventory.reconstitute(
            id = "inv-$date",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            date = date,
            totalCount = 3,
            reservedCount = 1,
            blockedCount = 0,
            heldCount = 0,
            version = 1L,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )

    given("재고 hold 생성 시") {
        `when`("숙박 날짜 범위의 모든 재고가 가용하면") {
            val checkIn = LocalDate.of(2026, 4, 1)
            val checkOut = LocalDate.of(2026, 4, 3)
            val firstDate = checkIn
            val secondDate = checkIn.plusDays(1)
            val savedHold = slot<InventoryHold>()

            every { inventoryAccess.getOrThrow("prop-1", "rt-1", firstDate) } returns inventory(firstDate)
            every { inventoryAccess.getOrThrow("prop-1", "rt-1", secondDate) } returns inventory(secondDate)
            every { inventoryAccess.saveAndEvict(any()) } answers { firstArg() }
            every { inventoryHoldRepository.save(capture(savedHold)) } answers { firstArg() }

            val result = sut.hold(
                reservationIntentId = "intent-1",
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                dateRange = DateRange.of(checkIn, checkOut),
                quantity = 1,
                expiresAt = fixedInstant.plusSeconds(900)
            )

            then("각 날짜 재고를 hold하고 InventoryHold를 저장한다") {
                result.id shouldBe "hold-1"
                result.reservationIntentId shouldBe "intent-1"
                result.dates shouldBe listOf(firstDate, secondDate)
                savedHold.captured.quantity shouldBe 1
                verify(exactly = 2) {
                    inventoryAccess.saveAndEvict(match { it.heldCount == 1 })
                }
            }
        }
    }
})
