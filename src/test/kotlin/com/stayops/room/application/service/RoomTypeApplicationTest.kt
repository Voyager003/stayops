package com.stayops.room.application.service

import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal

class RoomTypeApplicationTest : BehaviorSpec({

    val roomTypeRepository = mockk<RoomTypeRepository>()
    val roomTypeApplication = RoomTypeApplication(roomTypeRepository)

    fun newRoomType(id: String = "rt-1", propertyId: String = "prop-1", name: String = "디럭스") =
        RoomType.create(
            id = id,
            propertyId = propertyId,
            name = name,
            description = "넓은 더블룸",
            maxOccupancy = 2,
            basePrice = Money.won(150_000)
        )

    given("객실타입 생성 시") {
        `when`("중복 이름이 없으면") {
            every { roomTypeRepository.findByPropertyIdAndName("prop-1", "디럭스") } returns null
            every { roomTypeRepository.save(any()) } answers { firstArg() }

            val result = roomTypeApplication.createRoomType(
                propertyId = "prop-1",
                name = "디럭스",
                description = "넓은 더블룸",
                maxOccupancy = 2,
                basePrice = BigDecimal("150000")
            )

            then("저장된 객실타입을 반환한다") {
                result.name shouldBe "디럭스"
                result.propertyId shouldBe "prop-1"
                result.maxOccupancy shouldBe 2
            }
            then("Repository.save()가 한 번 호출된다") {
                verify(exactly = 1) { roomTypeRepository.save(any()) }
            }
        }

        `when`("동일한 이름이 이미 존재하면") {
            every { roomTypeRepository.findByPropertyIdAndName("prop-1", "디럭스") } returns newRoomType()

            then("ConflictException이 발생한다") {
                shouldThrow<ConflictException> {
                    roomTypeApplication.createRoomType(
                        propertyId = "prop-1",
                        name = "디럭스",
                        description = "설명",
                        maxOccupancy = 2,
                        basePrice = BigDecimal("150000")
                    )
                }
            }
        }
    }

    given("객실타입 단건 조회 시") {
        `when`("존재하는 id이면") {
            every { roomTypeRepository.findById("rt-1") } returns newRoomType()

            val result = roomTypeApplication.getRoomType("rt-1")

            then("해당 객실타입을 반환한다") {
                result.id shouldBe "rt-1"
                result.name shouldBe "디럭스"
            }
        }

        `when`("존재하지 않는 id이면") {
            every { roomTypeRepository.findById("not-exist") } returns null

            then("NotFoundException이 발생한다") {
                shouldThrow<NotFoundException> {
                    roomTypeApplication.getRoomType("not-exist")
                }
            }
        }
    }

    given("숙소별 객실타입 목록 조회 시") {
        `when`("저장된 객실타입이 있으면") {
            val roomTypes = listOf(newRoomType("rt-1", "prop-1", "디럭스"), newRoomType("rt-2", "prop-1", "스위트"))
            every { roomTypeRepository.findByPropertyId("prop-1") } returns roomTypes

            val result = roomTypeApplication.getRoomTypesByProperty("prop-1")

            then("전체 목록을 반환한다") {
                result.size shouldBe 2
            }
        }
    }

    given("객실타입 정보 수정 시") {
        val existing = newRoomType()

        `when`("이름 중복 없이 수정하면") {
            every { roomTypeRepository.findById("rt-1") } returns existing
            every { roomTypeRepository.findByPropertyIdAndName("prop-1", "스위트") } returns null
            every { roomTypeRepository.save(any()) } answers { firstArg() }

            val result = roomTypeApplication.updateRoomType(
                id = "rt-1",
                name = "스위트",
                description = "넓은 스위트",
                maxOccupancy = 4,
                basePrice = BigDecimal("300000")
            )

            then("수정된 객실타입을 반환한다") {
                result.name shouldBe "스위트"
                result.maxOccupancy shouldBe 4
            }
        }

        `when`("다른 객실타입이 같은 이름을 가지면") {
            val other = newRoomType("rt-2", "prop-1", "스위트")
            every { roomTypeRepository.findById("rt-1") } returns existing
            every { roomTypeRepository.findByPropertyIdAndName("prop-1", "스위트") } returns other

            then("ConflictException이 발생한다") {
                shouldThrow<ConflictException> {
                    roomTypeApplication.updateRoomType(
                        id = "rt-1",
                        name = "스위트",
                        description = "설명",
                        maxOccupancy = 2,
                        basePrice = BigDecimal("150000")
                    )
                }
            }
        }
    }

    given("객실타입 삭제 시") {
        `when`("존재하는 객실타입이면") {
            every { roomTypeRepository.findById("rt-1") } returns newRoomType()
            every { roomTypeRepository.deleteById("rt-1") } returns Unit

            roomTypeApplication.deleteRoomType("rt-1")

            then("deleteById가 호출된다") {
                verify { roomTypeRepository.deleteById("rt-1") }
            }
        }

        `when`("존재하지 않는 객실타입이면") {
            every { roomTypeRepository.findById("unknown") } returns null

            then("NotFoundException이 발생한다") {
                shouldThrow<NotFoundException> {
                    roomTypeApplication.deleteRoomType("unknown")
                }
            }
        }
    }
})
