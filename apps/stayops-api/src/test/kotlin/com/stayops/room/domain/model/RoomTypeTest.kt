package com.stayops.room.domain.model

import com.stayops.shared.domain.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class RoomTypeTest : BehaviorSpec({

    fun newRoomType() = RoomType.create(
        id = "rt-1",
        propertyId = "prop-1",
        name = "디럭스 더블",
        description = "넓은 더블룸",
        maxOccupancy = 2,
        basePrice = Money.won(150_000),
        amenities = listOf("TV", "에어컨")
    )

    given("객실타입 생성 시") {
        `when`("유효한 정보로 생성하면") {
            val roomType = newRoomType()
            then("version이 0으로 초기화된다") {
                roomType.version shouldBe 0
            }
        }
        `when`("name이 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    RoomType.create(
                        id = "rt-1",
                        propertyId = "prop-1",
                        name = "",
                        description = "설명",
                        maxOccupancy = 2,
                        basePrice = Money.won(150_000)
                    )
                }
            }
        }
        `when`("maxOccupancy가 0이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    RoomType.create(
                        id = "rt-1",
                        propertyId = "prop-1",
                        name = "디럭스",
                        description = "설명",
                        maxOccupancy = 0,
                        basePrice = Money.won(150_000)
                    )
                }
            }
        }
        `when`("basePrice가 0이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    RoomType.create(
                        id = "rt-1",
                        propertyId = "prop-1",
                        name = "디럭스",
                        description = "설명",
                        maxOccupancy = 2,
                        basePrice = Money.ZERO
                    )
                }
            }
        }
    }

    given("객실타입 정보 수정 시") {
        val roomType = newRoomType()

        `when`("유효한 값으로 수정하면") {
            val updated = roomType.updateInfo(
                name = "스위트룸",
                description = "최고급 스위트",
                maxOccupancy = 4,
                basePrice = Money.won(300_000),
                amenities = listOf("TV", "에어컨", "욕조")
            )
            then("정보가 변경된다") {
                updated.name shouldBe "스위트룸"
                updated.description shouldBe "최고급 스위트"
                updated.maxOccupancy shouldBe 4
                updated.basePrice shouldBe Money.won(300_000)
                updated.amenities shouldBe listOf("TV", "에어컨", "욕조")
            }
        }
        `when`("maxOccupancy를 0으로 수정하면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    roomType.updateInfo(maxOccupancy = 0)
                }
            }
        }
    }
})
