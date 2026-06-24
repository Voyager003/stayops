package com.stayops.room.domain.model

import com.stayops.shared.domain.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

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

    given("객실타입 동일성 비교 시") {
        `when`("같은 id를 가진 객체를 비교하면") {
            val original = newRoomType()
            val updated = original.updateInfo(
                name = "스위트룸",
                description = "완전히 다른 설명",
                maxOccupancy = 4,
                basePrice = Money.won(300_000),
                amenities = listOf("TV", "욕조", "미니바")
            )

            then("동일한 객체로 본다") {
                updated shouldBe original
                updated.hashCode() shouldBe original.hashCode()
            }

            then("집합 조회에서도 같은 객체로 인식된다") {
                setOf(original).contains(updated) shouldBe true
            }
        }

        `when`("id가 다르면 나머지 값이 같아도 다른 객체로 본다") {
            val original = newRoomType()
            val other = RoomType.create(
                id = "rt-2",
                propertyId = original.propertyId,
                name = original.name,
                description = original.description,
                maxOccupancy = original.maxOccupancy,
                basePrice = original.basePrice,
                amenities = original.amenities
            )

            then("동일하지 않다") {
                other shouldNotBe original
            }
        }
    }

    given("객실타입 문자열 표현 시") {
        `when`("toString을 호출하면") {
            val roomType = newRoomType()

            then("핵심 식별 정보가 포함된다") {
                val description = roomType.toString()
                description shouldContain "rt-1"
                description shouldContain "prop-1"
                description shouldContain "디럭스 더블"
            }
        }
    }
})
