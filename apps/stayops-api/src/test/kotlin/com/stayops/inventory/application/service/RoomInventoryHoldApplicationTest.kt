package com.stayops.inventory.application.service

import com.stayops.inventory.domain.model.InventoryHold
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
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

    fun inventory(date: LocalDate, heldCount: Int = 0): RoomInventory =
        RoomInventory.reconstitute(
            id = "inv-$date",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            date = date,
            totalCount = 3,
            reservedCount = 1,
            blockedCount = 0,
            heldCount = heldCount,
            version = 1L,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )

    beforeTest {
        clearAllMocks()
    }

    given("재고 hold 생성 시") {
        `when`("숙박 날짜 범위의 모든 재고가 가용하면") {
            val checkIn = LocalDate.of(2026, 4, 1)
            val checkOut = LocalDate.of(2026, 4, 3)
            val firstDate = checkIn
            val secondDate = checkIn.plusDays(1)
            val savedHold = slot<InventoryHold>()
            val savedInventories = mutableListOf<RoomInventory>()

            every { inventoryAccess.getOrThrow("prop-1", "rt-1", firstDate) } returns inventory(firstDate)
            every { inventoryAccess.getOrThrow("prop-1", "rt-1", secondDate) } returns inventory(secondDate)
            every { inventoryAccess.saveAndEvict(capture(savedInventories)) } answers { firstArg() }
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
                savedInventories.map { it.date } shouldBe listOf(firstDate, secondDate)
                savedInventories.map { it.heldCount } shouldBe listOf(1, 1)
            }
        }
    }

    given("재고 hold 소비 시") {
        `when`("HELD 상태의 hold가 존재하면") {
            val checkIn = LocalDate.of(2026, 4, 1)
            val checkOut = LocalDate.of(2026, 4, 3)
            val firstDate = checkIn
            val secondDate = checkIn.plusDays(1)
            val savedHolds = mutableListOf<InventoryHold>()
            val savedInventories = mutableListOf<RoomInventory>()

            every { inventoryHoldRepository.findByReservationIntentId("intent-1") } returns InventoryHold.create(
                id = "hold-1",
                reservationIntentId = "intent-1",
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                dates = listOf(firstDate, secondDate),
                quantity = 1,
                expiresAt = fixedInstant.plusSeconds(900),
                now = fixedInstant
            )
            every { inventoryAccess.getOrThrow("prop-1", "rt-1", firstDate) } returns inventory(firstDate, heldCount = 1)
            every { inventoryAccess.getOrThrow("prop-1", "rt-1", secondDate) } returns inventory(secondDate, heldCount = 1)
            every { inventoryAccess.saveAndEvict(capture(savedInventories)) } answers { firstArg() }
            every { inventoryHoldRepository.save(capture(savedHolds)) } answers { firstArg() }

            sut.consume("intent-1")

            then("각 날짜의 hold를 예약 재고로 전환하고 hold를 소비 상태로 저장한다") {
                savedInventories.map { it.date } shouldBe listOf(firstDate, secondDate)
                savedInventories.map { it.heldCount } shouldBe listOf(0, 0)
                savedInventories.map { it.reservedCount } shouldBe listOf(2, 2)
                savedHolds.last().status.name shouldBe "CONSUMED"
            }
        }
    }
})
