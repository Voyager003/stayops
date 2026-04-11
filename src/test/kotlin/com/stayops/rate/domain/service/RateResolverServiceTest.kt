package com.stayops.rate.domain.service

import com.stayops.rate.domain.model.DayOfWeekRate
import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanType
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalDate

class RateResolverServiceTest : BehaviorSpec({

    val resolver = RateResolverService()
    val basePrice = Money.of(100_000)

    fun ratePlan(
        id: String = "rp-1",
        type: RatePlanType = RatePlanType.SEASONAL,
        dateRange: DateRange? = null,
        dayOfWeekRules: List<DayOfWeekRate>? = null,
        channelCode: String? = null,
        price: Money = Money.of(150_000),
        priority: Int = 30
    ) = RatePlan.create(
        id = id,
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        name = "테스트 요금제",
        type = type,
        dateRange = dateRange,
        dayOfWeekRules = dayOfWeekRules,
        channelCode = channelCode,
        price = price,
        priority = priority
    )

    val today = LocalDate.of(2026, 3, 13) // 금요일

    // ─────────────────────────────────────────
    // 기본 fallback
    // ─────────────────────────────────────────

    given("적용 가능한 요금제가 없으면") {
        `when`("요금을 결정하면") {
            val result = resolver.resolve(
                ratePlans = emptyList(),
                roomTypeBasePrice = basePrice,
                date = today,
                channelCode = null
            )

            then("basePrice를 반환한다") {
                result shouldBe basePrice
            }
        }
    }

    // ─────────────────────────────────────────
    // SEASONAL 적용
    // ─────────────────────────────────────────

    given("SEASONAL 요금제가 있을 때") {
        `when`("날짜가 범위 안에 있으면") {
            val seasonal = ratePlan(
                type = RatePlanType.SEASONAL,
                dateRange = DateRange.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)),
                price = Money.of(150_000),
                priority = 30
            )
            val result = resolver.resolve(listOf(seasonal), basePrice, today, null)

            then("시즌 요금을 반환한다") {
                result shouldBe Money.of(150_000)
            }
        }

        `when`("날짜가 범위 밖이면") {
            val seasonal = ratePlan(
                type = RatePlanType.SEASONAL,
                dateRange = DateRange.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 1)),
                price = Money.of(150_000),
                priority = 30
            )
            val result = resolver.resolve(listOf(seasonal), basePrice, today, null)

            then("basePrice를 반환한다") {
                result shouldBe basePrice
            }
        }
    }

    // ─────────────────────────────────────────
    // 우선순위
    // ─────────────────────────────────────────

    given("여러 요금제가 겹칠 때") {
        `when`("SPECIAL과 SEASONAL이 모두 적용 가능하면") {
            val seasonal = ratePlan(
                id = "rp-seasonal",
                type = RatePlanType.SEASONAL,
                dateRange = DateRange.of(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)),
                price = Money.of(150_000),
                priority = 30
            )
            val special = ratePlan(
                id = "rp-special",
                type = RatePlanType.SPECIAL,
                dateRange = DateRange.of(LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 14)),
                price = Money.of(80_000),
                priority = 100
            )
            val result = resolver.resolve(listOf(seasonal, special), basePrice, today, null)

            then("우선순위가 높은 SPECIAL 요금을 반환한다") {
                result shouldBe Money.of(80_000)
            }
        }
    }

    // ─────────────────────────────────────────
    // WEEKDAY 요일별 요금
    // ─────────────────────────────────────────

    given("WEEKDAY 요금제가 있을 때") {
        val weekday = ratePlan(
            type = RatePlanType.WEEKDAY,
            dayOfWeekRules = listOf(
                DayOfWeekRate(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), Money.of(130_000)),
                DayOfWeekRate(setOf(DayOfWeek.SUNDAY), Money.of(110_000))
            ),
            price = basePrice,
            priority = 20
        )

        `when`("금요일이면") {
            val result = resolver.resolve(listOf(weekday), basePrice, today, null) // 금요일
            then("금·토 요금을 반환한다") {
                result shouldBe Money.of(130_000)
            }
        }

        `when`("월요일이면") {
            val monday = LocalDate.of(2026, 3, 16)
            val result = resolver.resolve(listOf(weekday), basePrice, monday, null)
            then("요일 규칙에 없으므로 기본 가격을 반환한다") {
                result shouldBe basePrice
            }
        }
    }

    // ─────────────────────────────────────────
    // CHANNEL_SPECIFIC
    // ─────────────────────────────────────────

    given("CHANNEL_SPECIFIC 요금제가 있을 때") {
        val channelPlan = ratePlan(
            type = RatePlanType.CHANNEL_SPECIFIC,
            channelCode = "AGODA",
            price = Money.of(90_000),
            priority = 50
        )

        `when`("해당 채널로 조회하면") {
            val result = resolver.resolve(listOf(channelPlan), basePrice, today, "AGODA")
            then("채널 특화 요금을 반환한다") {
                result shouldBe Money.of(90_000)
            }
        }

        `when`("다른 채널로 조회하면") {
            val result = resolver.resolve(listOf(channelPlan), basePrice, today, "AIRBNB")
            then("basePrice를 반환한다") {
                result shouldBe basePrice
            }
        }
    }

    // ─────────────────────────────────────────
    // resolveForDateRange — 복수 날짜 합산
    // ─────────────────────────────────────────

    given("3박 예약 시") {
        val weekday = ratePlan(
            type = RatePlanType.WEEKDAY,
            dayOfWeekRules = listOf(
                DayOfWeekRate(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), Money.of(130_000))
            ),
            price = basePrice,
            priority = 20
        )
        // 금(13) 130,000 + 토(14) 130,000 + 일(15) 100,000 = 360,000
        val stayRange = DateRange.of(LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 16))

        `when`("날짜별 요금을 합산하면") {
            val result = resolver.resolveForDateRange(listOf(weekday), basePrice, stayRange, null)
            then("각 날짜별 요금이 합산된다") {
                result shouldBe Money.of(360_000)
            }
        }
    }

    // ─────────────────────────────────────────
    // dateRange가 null인 상시 적용 요금제
    // ─────────────────────────────────────────

    given("dateRange가 null인 요금제가 있으면") {
        val alwaysActive = ratePlan(
            type = RatePlanType.SEASONAL,
            dateRange = null,
            price = Money.of(120_000),
            priority = 30
        )

        `when`("아무 날짜로 조회해도") {
            val result = resolver.resolve(listOf(alwaysActive), basePrice, today, null)
            then("항상 적용된다") {
                result shouldBe Money.of(120_000)
            }
        }
    }
})
