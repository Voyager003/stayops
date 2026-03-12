package com.stayops.room.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

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
})
