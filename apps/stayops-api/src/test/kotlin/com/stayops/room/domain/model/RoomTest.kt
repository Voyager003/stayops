package com.stayops.room.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain

class RoomTest : BehaviorSpec({

    fun newRoom() = Room.create(
        id = "room-1",
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        roomNumber = "101",
        floor = 1
    )

    given("객실 생성 시") {
        `when`("유효한 정보로 생성하면") {
            val room = newRoom()
            then("AVAILABLE 상태로 생성된다") {
                room.status shouldBe RoomStatus.AVAILABLE
            }
            then("memo는 null로 초기화된다") {
                room.memo shouldBe null
            }
            then("version이 0으로 초기화된다") {
                room.version shouldBe 0
            }
        }
        `when`("roomNumber가 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Room.create(
                        id = "room-1",
                        propertyId = "prop-1",
                        roomTypeId = "rt-1",
                        roomNumber = "",
                        floor = 1
                    )
                }
            }
        }
        `when`("floor가 0이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Room.create(
                        id = "room-1",
                        propertyId = "prop-1",
                        roomTypeId = "rt-1",
                        roomNumber = "101",
                        floor = 0
                    )
                }
            }
        }
    }

    given("AVAILABLE 상태의 객실") {
        val room = newRoom()

        `when`("checkIn() 호출 시") {
            val occupied = room.checkIn()
            then("상태가 OCCUPIED로 변경된다") {
                occupied.status shouldBe RoomStatus.OCCUPIED
            }
        }
        `when`("startMaintenance() 호출 시") {
            val maintenance = room.startMaintenance()
            then("상태가 MAINTENANCE로 변경된다") {
                maintenance.status shouldBe RoomStatus.MAINTENANCE
            }
        }
        `when`("checkOut() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    room.checkOut()
                }
            }
        }
        `when`("completeCleaning() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    room.completeCleaning()
                }
            }
        }
        `when`("completeMaintenance() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    room.completeMaintenance()
                }
            }
        }
    }

    given("OCCUPIED 상태의 객실") {
        val room = newRoom().checkIn()

        `when`("checkOut() 호출 시") {
            val cleaning = room.checkOut()
            then("상태가 CLEANING으로 변경된다") {
                cleaning.status shouldBe RoomStatus.CLEANING
            }
        }
        `when`("checkIn() 재호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    room.checkIn()
                }
            }
        }
        `when`("startMaintenance() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    room.startMaintenance()
                }
            }
        }
    }

    given("CLEANING 상태의 객실") {
        val room = newRoom().checkIn().checkOut()

        `when`("completeCleaning() 호출 시") {
            val available = room.completeCleaning()
            then("상태가 AVAILABLE로 변경된다") {
                available.status shouldBe RoomStatus.AVAILABLE
            }
        }
        `when`("checkIn() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    room.checkIn()
                }
            }
        }
        `when`("checkOut() 재호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    room.checkOut()
                }
            }
        }
    }

    given("MAINTENANCE 상태의 객실") {
        val room = newRoom().startMaintenance()

        `when`("completeMaintenance() 호출 시") {
            val available = room.completeMaintenance()
            then("상태가 AVAILABLE로 변경된다") {
                available.status shouldBe RoomStatus.AVAILABLE
            }
        }
        `when`("checkIn() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    room.checkIn()
                }
            }
        }
        `when`("startMaintenance() 재호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    room.startMaintenance()
                }
            }
        }
    }

    given("객실 동일성 비교 시") {
        val room = newRoom()

        `when`("같은 ID를 가진 객실의 상태와 메모가 변경되면") {
            val changed = room.checkIn().updateMemo("연박 투숙객 요청사항")

            then("동일한 객실로 판단한다") {
                changed shouldBe room
                changed.hashCode() shouldBe room.hashCode()
            }

            then("기존 객실을 담은 Set에서 조회할 수 있다") {
                setOf(room).contains(changed) shouldBe true
            }
        }

        `when`("도메인 속성이 같아도 ID가 다르면") {
            val other = Room.create(
                id = "room-2",
                propertyId = room.propertyId,
                roomTypeId = room.roomTypeId,
                roomNumber = room.roomNumber,
                floor = room.floor
            )

            then("다른 객실로 판단한다") {
                other shouldNotBe room
            }
        }

        `when`("객실을 문자열로 표현하면") {
            val roomWithMemo = room.updateMemo("장기 투숙객 요청사항")

            then("운영 메모를 노출하지 않는다") {
                roomWithMemo.toString() shouldNotContain "장기 투숙객 요청사항"
            }
        }
    }
})
