package com.stayops.inventory.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryCache
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate

class RoomInventoryReservationApplicationTest : BehaviorSpec({

    val inventoryRepository = mockk<RoomInventoryRepository>()
    val cache = mockk<RoomInventoryCache>()
    val inventoryAccess = RoomInventoryAccessApplication(
        inventoryRepository = inventoryRepository,
        cache = cache
    )
    val sut = RoomInventoryReservationApplication(
        inventoryAccess = inventoryAccess
    )

    val date = LocalDate.of(2026, 3, 12)
    val fixedInstant = Instant.parse("2026-03-12T00:00:00Z")

    fun inventory(
        reservedCount: Int = 0,
        blockedCount: Int = 0
    ) = RoomInventory.reconstitute(
        id = "inv-1",
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        date = date,
        totalCount = 5,
        reservedCount = reservedCount,
        blockedCount = blockedCount,
        version = 0L,
        createdAt = fixedInstant,
        updatedAt = fixedInstant
    )

    given("예약 재고 점유는") {
        `when`("캐시에 재고가 있으면") {
            then("예약 수를 증가시키고 저장 후 캐시를 무효화한다") {
                clearAllMocks()
                every { cache.get("prop-1", "rt-1", date) } returns inventory()
                every { inventoryRepository.save(any()) } answers { firstArg() }
                val evictedRoomType = slot<String>()
                justRun { cache.evict("prop-1", capture(evictedRoomType), date) }

                sut.reserve("prop-1", "rt-1", date)

                verify {
                    inventoryRepository.save(match {
                        it.reservedCount == 1 && it.availableCount == 4
                    })
                }
                evictedRoomType.captured shouldBe "rt-1"
            }
        }

        `when`("캐시 조회가 실패하면") {
            then("DB 조회로 대체해 예약 점유를 완료한다") {
                clearAllMocks()
                every { cache.get("prop-1", "rt-1", date) } throws RuntimeException("redis down")
                every { inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", date) } returns inventory()
                every { cache.put(any()) } throws RuntimeException("redis down")
                every { inventoryRepository.save(any()) } answers { firstArg() }
                every { cache.evict(any(), any(), any()) } throws RuntimeException("redis down")

                sut.reserve("prop-1", "rt-1", date)

                verify {
                    inventoryRepository.save(match { it.reservedCount == 1 })
                }
            }
        }

        `when`("재고가 없으면") {
            then("NotFoundException을 전파한다") {
                clearAllMocks()
                every { cache.get("prop-1", "rt-1", date) } returns null
                every { inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", date) } returns null

                shouldThrow<NotFoundException> {
                    sut.reserve("prop-1", "rt-1", date)
                }
            }
        }
    }

    given("예약 재고 복원은") {
        `when`("예약 점유 수가 있으면") {
            then("예약 수를 감소시키고 저장 후 캐시를 무효화한다") {
                clearAllMocks()
                every { cache.get("prop-1", "rt-1", date) } returns inventory(reservedCount = 2)
                every { inventoryRepository.save(any()) } answers { firstArg() }
                justRun { cache.evict(any(), any(), any()) }

                sut.release("prop-1", "rt-1", date)

                verify {
                    inventoryRepository.save(match {
                        it.reservedCount == 1 && it.availableCount == 4
                    })
                }
            }
        }

        `when`("예약 점유 수가 없으면") {
            then("도메인 예외를 전파하고 저장하지 않는다") {
                clearAllMocks()
                every { cache.get("prop-1", "rt-1", date) } returns inventory(reservedCount = 0)

                shouldThrow<IllegalArgumentException> {
                    sut.release("prop-1", "rt-1", date)
                }
                verify(exactly = 0) { inventoryRepository.save(any()) }
            }
        }
    }
})
