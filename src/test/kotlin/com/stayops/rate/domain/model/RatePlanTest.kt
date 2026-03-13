package com.stayops.rate.domain.model

import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalDate

class RatePlanTest : BehaviorSpec({

    val basePrice = Money.of(100_000)

    // ─────────────────────────────────────────
    // RatePlan 생성
    // ─────────────────────────────────────────

    given("RatePlan을 생성할 때") {
        `when`("유효한 정보로 SEASONAL 요금제를 생성하면") {
            val ratePlan = RatePlan.create(
                id = "rp-1",
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                name = "성수기 요금",
                type = RatePlanType.SEASONAL,
                dateRange = DateRange.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 1)),
                dayOfWeekRules = null,
                channelCode = null,
                price = Money.of(150_000),
                priority = 30
            )

            then("상태가 ACTIVE이다") {
                ratePlan.status shouldBe RatePlanStatus.ACTIVE
            }
            then("type이 SEASONAL이다") {
                ratePlan.type shouldBe RatePlanType.SEASONAL
            }
            then("우선순위가 30이다") {
                ratePlan.priority shouldBe 30
            }
        }

        `when`("이름이 공백이면") {
            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    RatePlan.create(
                        id = "rp-1",
                        propertyId = "prop-1",
                        roomTypeId = "rt-1",
                        name = "  ",
                        type = RatePlanType.SEASONAL,
                        dateRange = null,
                        dayOfWeekRules = null,
                        channelCode = null,
                        price = basePrice,
                        priority = 30
                    )
                }
            }
        }

        `when`("우선순위가 0 이하이면") {
            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    RatePlan.create(
                        id = "rp-1",
                        propertyId = "prop-1",
                        roomTypeId = "rt-1",
                        name = "잘못된 요금제",
                        type = RatePlanType.SEASONAL,
                        dateRange = null,
                        dayOfWeekRules = null,
                        channelCode = null,
                        price = basePrice,
                        priority = 0
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────
    // 상태 전이
    // ─────────────────────────────────────────

    given("ACTIVE 상태의 RatePlan이") {
        val ratePlan = RatePlan.create(
            id = "rp-1",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            name = "기본 요금",
            type = RatePlanType.SEASONAL,
            dateRange = null,
            dayOfWeekRules = null,
            channelCode = null,
            price = basePrice,
            priority = 30
        )

        `when`("비활성화하면") {
            val deactivated = ratePlan.deactivate()
            then("상태가 INACTIVE로 변경된다") {
                deactivated.status shouldBe RatePlanStatus.INACTIVE
            }
        }
    }

    given("INACTIVE 상태의 RatePlan이") {
        val ratePlan = RatePlan.create(
            id = "rp-1",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            name = "기본 요금",
            type = RatePlanType.SEASONAL,
            dateRange = null,
            dayOfWeekRules = null,
            channelCode = null,
            price = basePrice,
            priority = 30
        ).deactivate()

        `when`("활성화하면") {
            val activated = ratePlan.activate()
            then("상태가 ACTIVE로 변경된다") {
                activated.status shouldBe RatePlanStatus.ACTIVE
            }
        }
    }

    // ─────────────────────────────────────────
    // DayOfWeekRate
    // ─────────────────────────────────────────

    given("WEEKDAY 타입 요금제에") {
        val weekdayPlan = RatePlan.create(
            id = "rp-2",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            name = "주말 요금",
            type = RatePlanType.WEEKDAY,
            dateRange = null,
            dayOfWeekRules = listOf(
                DayOfWeekRate(
                    daysOfWeek = setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY),
                    price = Money.of(130_000)
                )
            ),
            channelCode = null,
            price = basePrice,
            priority = 20
        )

        `when`("요일별 요금을 조회하면") {
            then("금요일은 130,000원이다") {
                weekdayPlan.priceForDate(LocalDate.of(2026, 3, 13)) shouldBe Money.of(130_000) // 금요일
            }
            then("월요일은 기본 가격이다") {
                weekdayPlan.priceForDate(LocalDate.of(2026, 3, 16)) shouldBe basePrice // 월요일
            }
        }
    }
})
