package com.stayops.inventory.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class RoomInventoryTest : BehaviorSpec({

    val today = LocalDate.of(2026, 3, 12)

    fun newInventory(
        totalCount: Int = 5,
        reservedCount: Int = 0,
        blockedCount: Int = 0
    ) = RoomInventory.reconstitute(
        id = "inv-1",
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        date = today,
        totalCount = totalCount,
        reservedCount = reservedCount,
        blockedCount = blockedCount,
        version = 0,
        createdAt = java.time.Instant.now(),
        updatedAt = java.time.Instant.now()
    )

    given("재고 생성 시") {
        `when`("유효한 정보로 생성하면") {
            val inventory = RoomInventory.create(
                id = "inv-1",
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                date = today,
                totalCount = 5
            )
            then("reservedCount와 blockedCount가 0으로 초기화된다") {
                inventory.reservedCount shouldBe 0
                inventory.blockedCount shouldBe 0
                inventory.availableCount shouldBe 5
            }
            then("version이 0으로 초기화된다") {
                inventory.version shouldBe 0
            }
        }
        `when`("totalCount가 0이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    RoomInventory.create("inv-1", "prop-1", "rt-1", today, totalCount = 0)
                }
            }
        }
    }

    given("availableCount 계산 시") {
        `when`("total=5, reserved=2, blocked=1이면") {
            val inventory = newInventory(totalCount = 5, reservedCount = 2, blockedCount = 1)
            then("availableCount는 2이다") {
                inventory.availableCount shouldBe 2
            }
        }
    }

    given("reserve() 호출 시") {
        `when`("가용 객실이 있으면") {
            val inventory = newInventory(totalCount = 3, reservedCount = 1, blockedCount = 0)
            val reserved = inventory.reserve()
            then("reservedCount가 1 증가한다") {
                reserved.reservedCount shouldBe 2
                reserved.availableCount shouldBe 1
            }
        }
        `when`("가용 객실이 0이면") {
            val inventory = newInventory(totalCount = 2, reservedCount = 1, blockedCount = 1)
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    inventory.reserve()
                }
            }
        }
        `when`("마지막 1실을 예약하면") {
            val inventory = newInventory(totalCount = 1, reservedCount = 0, blockedCount = 0)
            val reserved = inventory.reserve()
            then("availableCount가 0이 된다") {
                reserved.availableCount shouldBe 0
                reserved.reservedCount shouldBe 1
            }
        }
    }

    given("release() 호출 시") {
        `when`("예약된 객실이 있으면") {
            val inventory = newInventory(totalCount = 3, reservedCount = 2, blockedCount = 0)
            val released = inventory.release()
            then("reservedCount가 1 감소한다") {
                released.reservedCount shouldBe 1
                released.availableCount shouldBe 2
            }
        }
        `when`("예약된 객실이 0이면") {
            val inventory = newInventory(totalCount = 3, reservedCount = 0, blockedCount = 0)
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    inventory.release()
                }
            }
        }
    }

    given("block() 호출 시") {
        `when`("가용 객실이 충분하면") {
            val inventory = newInventory(totalCount = 5, reservedCount = 1, blockedCount = 1)
            val blocked = inventory.block(2)
            then("blockedCount가 증가한다") {
                blocked.blockedCount shouldBe 3
                blocked.availableCount shouldBe 1
            }
        }
        `when`("가용 객실보다 많은 수를 차단하려 하면") {
            val inventory = newInventory(totalCount = 3, reservedCount = 1, blockedCount = 1)
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    inventory.block(2)
                }
            }
        }
        `when`("count가 0이면") {
            val inventory = newInventory()
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    inventory.block(0)
                }
            }
        }
    }

    given("unblock() 호출 시") {
        `when`("차단된 객실이 충분하면") {
            val inventory = newInventory(totalCount = 5, reservedCount = 0, blockedCount = 3)
            val unblocked = inventory.unblock(2)
            then("blockedCount가 감소한다") {
                unblocked.blockedCount shouldBe 1
                unblocked.availableCount shouldBe 4
            }
        }
        `when`("차단된 객실보다 많은 수를 해제하려 하면") {
            val inventory = newInventory(totalCount = 5, reservedCount = 0, blockedCount = 1)
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    inventory.unblock(2)
                }
            }
        }
        `when`("count가 0이면") {
            val inventory = newInventory(totalCount = 5, reservedCount = 0, blockedCount = 3)
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    inventory.unblock(0)
                }
            }
        }
    }
})
