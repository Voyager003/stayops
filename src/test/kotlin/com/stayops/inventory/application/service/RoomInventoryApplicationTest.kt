package com.stayops.inventory.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.inventory.infrastructure.cache.RedisRoomInventoryCache
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.dao.OptimisticLockingFailureException
import java.time.Instant
import java.time.LocalDate

class RoomInventoryApplicationTest : BehaviorSpec({

    val inventoryRepository = mockk<RoomInventoryRepository>()
    val cache = mockk<RedisRoomInventoryCache>()
    val roomRepository = mockk<RoomRepository>()
    val inventoryApplication = RoomInventoryApplication(inventoryRepository, cache, roomRepository)

    val today = LocalDate.of(2026, 3, 12)

    fun newInventory(
        id: String = "inv-1",
        propertyId: String = "prop-1",
        roomTypeId: String = "rt-1",
        date: LocalDate = today,
        totalCount: Int = 5,
        reservedCount: Int = 0,
        blockedCount: Int = 0
    ) = RoomInventory.reconstitute(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        date = date,
        totalCount = totalCount,
        reservedCount = reservedCount,
        blockedCount = blockedCount,
        version = 0L,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    given("판매 오픈 시") {
        `when`("날짜 범위에 재고가 없으면") {
            val rooms = listOf(
                Room.create("r-1", "prop-1", "rt-1", "101", 1),
                Room.create("r-2", "prop-1", "rt-1", "102", 1),
                Room.create("r-3", "prop-1", "rt-1", "103", 1)
            )
            every { roomRepository.findByRoomTypeId("rt-1") } returns rooms
            every {
                inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween("prop-1", "rt-1", today, today.plusDays(2))
            } returns emptyList()
            every { inventoryRepository.save(any()) } answers { firstArg() }

            val created = inventoryApplication.openInventory("prop-1", "rt-1", today, today.plusDays(2))

            then("3일치 재고가 생성되고 totalCount는 Room 수와 같다") {
                created shouldBe 3
                verify(exactly = 3) { inventoryRepository.save(match { it.totalCount == 3 }) }
            }
        }

        `when`("날짜 범위에 이미 재고가 있는 날짜는 건너뛴다") {
            val rooms = listOf(Room.create("r-1", "prop-1", "rt-1", "101", 1))
            every { roomRepository.findByRoomTypeId("rt-1") } returns rooms
            every {
                inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween("prop-1", "rt-1", today, today.plusDays(2))
            } returns listOf(newInventory(date = today, totalCount = 1))
            every { inventoryRepository.save(any()) } answers { firstArg() }

            val created = inventoryApplication.openInventory("prop-1", "rt-1", today, today.plusDays(2))

            then("기존 재고가 있는 날짜를 제외하고 생성한다") {
                created shouldBe 2
            }
        }

        `when`("등록된 객실이 없으면") {
            every { roomRepository.findByRoomTypeId("rt-1") } returns emptyList()

            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    inventoryApplication.openInventory("prop-1", "rt-1", today, today.plusDays(2))
                }
            }
        }
    }

    given("가용성 조회 시") {
        `when`("날짜 범위를 요청하면") {
            val inventories = listOf(
                newInventory(id = "inv-1", date = today),
                newInventory(id = "inv-2", date = today.plusDays(1))
            )
            every {
                inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                    "prop-1", "rt-1", today, today.plusDays(1)
                )
            } returns inventories

            val result = inventoryApplication.getAvailability("prop-1", "rt-1", today, today.plusDays(1))

            then("날짜 범위의 재고 목록을 반환한다") {
                result.size shouldBe 2
            }
        }
    }

    given("재고 차단 시") {
        `when`("캐시에 재고가 있고 가용 재고가 충분하면") {
            val inventory = newInventory(totalCount = 5, blockedCount = 0)
            every { cache.get("prop-1", "rt-1", today) } returns inventory
            every { inventoryRepository.save(any()) } answers { firstArg() }
            val evictedSlot = slot<String>()
            justRun { cache.evict(any(), capture(evictedSlot), any()) }

            val result = inventoryApplication.blockInventory("prop-1", "rt-1", today, 2)

            then("차단 후 캐시를 무효화한다") {
                result.blockedCount shouldBe 2
                result.availableCount shouldBe 3
                evictedSlot.captured shouldBe "rt-1"
            }
        }

        `when`("캐시에 없으면 DB에서 조회한다") {
            val inventory = newInventory(totalCount = 5, blockedCount = 0)
            every { cache.get("prop-1", "rt-1", today) } returns null
            every { inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today) } returns inventory
            val cachedSlot = slot<RoomInventory>()
            justRun { cache.put(capture(cachedSlot)) }
            every { inventoryRepository.save(any()) } answers { firstArg() }
            justRun { cache.evict(any(), any(), any()) }

            inventoryApplication.blockInventory("prop-1", "rt-1", today, 1)

            then("DB 조회 후 캐시에 put한다") {
                cachedSlot.captured shouldNotBe null
                cachedSlot.captured.id shouldBe "inv-1"
            }
        }

        `when`("재고를 찾을 수 없으면") {
            every { cache.get("prop-1", "rt-1", today) } returns null
            every { inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today) } returns null

            then("NotFoundException이 발생한다") {
                shouldThrow<NotFoundException> {
                    inventoryApplication.blockInventory("prop-1", "rt-1", today, 1)
                }
            }
        }

        `when`("낙관적 락 충돌이 발생하면") {
            val inventory = newInventory(totalCount = 5)
            every { cache.get("prop-1", "rt-1", today) } returns inventory
            every { inventoryRepository.save(any()) } throws OptimisticLockingFailureException("conflict")

            then("ConflictException으로 변환된다") {
                shouldThrow<ConflictException> {
                    inventoryApplication.blockInventory("prop-1", "rt-1", today, 1)
                }
            }
        }
    }

    given("재고 차단 해제 시") {
        `when`("차단된 재고가 있으면") {
            val inventory = newInventory(totalCount = 5, blockedCount = 3)
            every { cache.get("prop-1", "rt-1", today) } returns inventory
            every { inventoryRepository.save(any()) } answers { firstArg() }
            val evictedSlot = slot<String>()
            justRun { cache.evict(any(), capture(evictedSlot), any()) }

            val result = inventoryApplication.unblockInventory("prop-1", "rt-1", today, 2)

            then("차단 해제 후 캐시를 무효화한다") {
                result.blockedCount shouldBe 1
                result.availableCount shouldBe 4
                evictedSlot.captured shouldBe "rt-1"
            }
        }
    }
})
