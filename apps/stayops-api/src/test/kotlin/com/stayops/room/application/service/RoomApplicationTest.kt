package com.stayops.room.application.service

import com.stayops.inventory.application.provided.RoomInventorySynchronizer
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomStatus
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class RoomApplicationTest : BehaviorSpec({

    val roomRepository = mockk<RoomRepository>()
    val roomInventorySyncPort = mockk<RoomInventorySynchronizer>()
    val idGenerator = object : IdGenerator {
        override fun generate() = "room-new"
    }
    val roomApplication = RoomApplication(roomRepository, roomInventorySyncPort, idGenerator)

    fun newRoom(id: String = "room-1", propertyId: String = "prop-1", roomNumber: String = "101") =
        Room.create(
            id = id,
            propertyId = propertyId,
            roomTypeId = "rt-1",
            roomNumber = roomNumber,
            floor = 1
        )

    given("객실 생성 시") {
        `when`("중복 호수가 없으면") {
            every { roomRepository.findByPropertyIdAndRoomNumber("prop-1", "101") } returns null
            every { roomRepository.save(any()) } answers { firstArg() }
            justRun { roomInventorySyncPort.syncInventoryForRoomType("prop-1", "rt-1") }

            val result = roomApplication.createRoom(
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                roomNumber = "101",
                floor = 1
            )

            then("AVAILABLE 상태로 저장된 객실을 반환한다") {
                result.roomNumber shouldBe "101"
                result.status shouldBe RoomStatus.AVAILABLE
            }
            then("Repository.save()가 한 번 호출된다") {
                verify(exactly = 1) { roomRepository.save(any()) }
            }
            then("객실 수 변경을 재고와 채널에 반영하기 위해 객실타입 재고 동기화를 요청한다") {
                verify(exactly = 1) { roomInventorySyncPort.syncInventoryForRoomType("prop-1", "rt-1") }
            }
        }

        `when`("동일한 호수가 이미 존재하면") {
            every { roomRepository.findByPropertyIdAndRoomNumber("prop-1", "101") } returns newRoom()

            then("ConflictException이 발생한다") {
                shouldThrow<ConflictException> {
                    roomApplication.createRoom(
                        propertyId = "prop-1",
                        roomTypeId = "rt-1",
                        roomNumber = "101",
                        floor = 1
                    )
                }
            }
        }
    }

    given("객실 단건 조회 시") {
        `when`("존재하는 id이면") {
            every { roomRepository.findById("room-1") } returns newRoom()

            val result = roomApplication.getRoom("room-1")

            then("해당 객실을 반환한다") {
                result.id shouldBe "room-1"
                result.roomNumber shouldBe "101"
            }
        }

        `when`("존재하지 않는 id이면") {
            every { roomRepository.findById("not-exist") } returns null

            then("NotFoundException이 발생한다") {
                shouldThrow<NotFoundException> {
                    roomApplication.getRoom("not-exist")
                }
            }
        }
    }

    given("객실 상태 전이 시") {
        `when`("AVAILABLE 객실에 checkIn을 호출하면") {
            every { roomRepository.findById("room-1") } returns newRoom()
            every { roomRepository.save(any()) } answers { firstArg() }

            val result = roomApplication.checkIn("room-1")

            then("상태가 OCCUPIED로 변경된다") {
                result.status shouldBe RoomStatus.OCCUPIED
            }
        }

        `when`("OCCUPIED 객실에 checkOut을 호출하면") {
            every { roomRepository.findById("room-1") } returns newRoom().checkIn()
            every { roomRepository.save(any()) } answers { firstArg() }

            val result = roomApplication.checkOut("room-1")

            then("상태가 CLEANING으로 변경된다") {
                result.status shouldBe RoomStatus.CLEANING
            }
        }

        `when`("CLEANING 객실에 completeCleaning을 호출하면") {
            every { roomRepository.findById("room-1") } returns newRoom().checkIn().checkOut()
            every { roomRepository.save(any()) } answers { firstArg() }

            val result = roomApplication.completeCleaning("room-1")

            then("상태가 AVAILABLE로 변경된다") {
                result.status shouldBe RoomStatus.AVAILABLE
            }
        }

        `when`("AVAILABLE 객실에 startMaintenance를 호출하면") {
            every { roomRepository.findById("room-1") } returns newRoom()
            every { roomRepository.save(any()) } answers { firstArg() }

            val result = roomApplication.startMaintenance("room-1")

            then("상태가 MAINTENANCE로 변경된다") {
                result.status shouldBe RoomStatus.MAINTENANCE
            }
        }

        `when`("MAINTENANCE 객실에 completeMaintenance를 호출하면") {
            every { roomRepository.findById("room-1") } returns newRoom().startMaintenance()
            every { roomRepository.save(any()) } answers { firstArg() }

            val result = roomApplication.completeMaintenance("room-1")

            then("상태가 AVAILABLE로 변경된다") {
                result.status shouldBe RoomStatus.AVAILABLE
            }
        }

        `when`("잘못된 전이이면") {
            every { roomRepository.findById("room-1") } returns newRoom()

            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    roomApplication.checkOut("room-1")
                }
            }
        }
    }

    given("메모 수정 시") {
        `when`("메모를 입력하면") {
            every { roomRepository.findById("room-1") } returns newRoom()
            every { roomRepository.save(any()) } answers { firstArg() }

            val result = roomApplication.updateMemo("room-1", "VIP 고객 객실")

            then("메모가 변경된다") {
                result.memo shouldBe "VIP 고객 객실"
            }
        }

        `when`("메모를 null로 입력하면") {
            every { roomRepository.findById("room-1") } returns newRoom().updateMemo("기존 메모")
            every { roomRepository.save(any()) } answers { firstArg() }

            val result = roomApplication.updateMemo("room-1", null)

            then("메모가 null로 변경된다") {
                result.memo shouldBe null
            }
        }
    }
})
