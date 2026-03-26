package com.stayops.rate.application.service

import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.model.RatePlanType
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import java.time.LocalDate

class RatePlanApplicationTest : BehaviorSpec({

    val ratePlanRepository = mockk<RatePlanRepository>()
    val ratePlanApplication = RatePlanApplication(ratePlanRepository)

    fun testRatePlan(
        id: String = "rp-1",
        type: RatePlanType = RatePlanType.SEASONAL,
        price: Money = Money.of(150_000),
        priority: Int = 30
    ) = RatePlan.create(
        id = id,
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        name = "테스트 요금제",
        type = type,
        dateRange = DateRange.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 1)),
        dayOfWeekRules = null,
        channelCode = null,
        price = price,
        priority = priority
    )

    // ─────────────────────────────────────────
    // 요금제 생성
    // ─────────────────────────────────────────

    given("요금제 생성 시") {
        `when`("유효한 정보로 생성하면") {
            val savedSlot = slot<RatePlan>()
            every { ratePlanRepository.save(capture(savedSlot)) } answers { firstArg() }

            val result = ratePlanApplication.createRatePlan(
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

            then("요금제가 생성된다") {
                result.name shouldBe "성수기 요금"
                result.status shouldBe RatePlanStatus.ACTIVE
                savedSlot.captured.name shouldBe "성수기 요금"
            }
        }
    }

    // ─────────────────────────────────────────
    // 요금제 조회
    // ─────────────────────────────────────────

    given("요금제 목록 조회 시") {
        `when`("숙소 ID로 조회하면") {
            val plans = listOf(testRatePlan(id = "rp-1"), testRatePlan(id = "rp-2"))
            every { ratePlanRepository.findByPropertyId("prop-1") } returns plans
            val result = ratePlanApplication.getRatePlans("prop-1")

            then("전체 목록을 반환한다") {
                result.size shouldBe 2
            }
        }
    }

    // ─────────────────────────────────────────
    // 요금제 삭제
    // ─────────────────────────────────────────

    given("요금제 삭제 시") {
        `when`("존재하는 요금제를 삭제하면") {
            every { ratePlanRepository.findById("rp-1") } returns testRatePlan()
            justRun { ratePlanRepository.deleteById("rp-1") }
            ratePlanApplication.deleteRatePlan("rp-1")

            then("정상적으로 삭제된다") {
                // deleteById가 호출됨 — 예외 없으면 성공
            }
        }

        `when`("존재하지 않는 요금제를 삭제하면") {
            every { ratePlanRepository.findById("unknown") } returns null

            then("NotFoundException이 발생한다") {
                shouldThrow<NotFoundException> {
                    ratePlanApplication.deleteRatePlan("unknown")
                }
            }
        }
    }

    // ─────────────────────────────────────────
    // 요금제 수정
    // ─────────────────────────────────────────

    given("요금제 수정 시") {
        `when`("유효한 정보로 수정하면") {
            every { ratePlanRepository.findById("rp-1") } returns testRatePlan()
            every { ratePlanRepository.save(any()) } answers { firstArg() }

            val result = ratePlanApplication.updateRatePlan(
                id = "rp-1",
                name = "수정된 요금제",
                dateRange = DateRange.of(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 1)),
                dayOfWeekRules = null,
                channelCode = null,
                price = Money.of(180_000),
                priority = 40
            )

            then("수정된 내용이 반영된다") {
                result.name shouldBe "수정된 요금제"
                result.price shouldBe Money.of(180_000)
                result.priority shouldBe 40
            }
        }
    }

    // ─────────────────────────────────────────
    // 요금제 활성화/비활성화
    // ─────────────────────────────────────────

    given("요금제 상태 변경 시") {
        `when`("활성 요금제를 비활성화하면") {
            every { ratePlanRepository.findById("rp-1") } returns testRatePlan()
            every { ratePlanRepository.save(any()) } answers { firstArg() }

            val result = ratePlanApplication.deactivateRatePlan("rp-1")

            then("상태가 INACTIVE로 변경된다") {
                result.status shouldBe RatePlanStatus.INACTIVE
            }
        }

        `when`("비활성 요금제를 활성화하면") {
            val inactive = testRatePlan().deactivate()
            every { ratePlanRepository.findById("rp-1") } returns inactive
            every { ratePlanRepository.save(any()) } answers { firstArg() }

            val result = ratePlanApplication.activateRatePlan("rp-1")

            then("상태가 ACTIVE로 변경된다") {
                result.status shouldBe RatePlanStatus.ACTIVE
            }
        }
    }

    // ─────────────────────────────────────────
    // 요금 미리보기
    // ─────────────────────────────────────────

    given("요금 미리보기 시") {
        `when`("적용 가능한 요금제가 있으면") {
            val seasonal = testRatePlan(price = Money.of(150_000))
            every {
                ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus("prop-1", "rt-1", RatePlanStatus.ACTIVE)
            } returns listOf(seasonal)

            val result = ratePlanApplication.previewRates(
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                basePrice = Money.of(100_000),
                startDate = LocalDate.of(2026, 7, 1),
                endDate = LocalDate.of(2026, 7, 3),
                channelCode = null
            )

            then("날짜별 요금 목록을 반환한다") {
                result.size shouldBe 2
                result[0].price shouldBe Money.of(150_000)
                result[1].price shouldBe Money.of(150_000)
            }
        }
    }
})
