package com.stayops.guest.domain.model

import com.stayops.shared.domain.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class GuestTest : BehaviorSpec({
    val visitDate = LocalDate.of(2026, 3, 12)

    fun newGuest(
        id: String = "guest-1",
        propertyId: String = "prop-1",
        name: String = "홍길동",
        phone: String = "010-1234-5678",
        email: String? = null,
        memo: String? = null,
    ): Guest = Guest.create(
        id = id,
        propertyId = propertyId,
        name = name,
        phone = phone,
        email = email,
        memo = memo,
    )

    // ─────────────────────────────────────────
    // Guest 생성
    // ─────────────────────────────────────────

    given("Guest를 생성할 때") {
        `when`("유효한 정보로 생성하면") {
            val guest = newGuest()

            then("초기 tier는 NEW이다") {
                guest.tier shouldBe GuestTier.NEW
            }
            then("visitSummary는 빈 상태이다") {
                guest.visitSummary.totalVisits shouldBe 0
                guest.visitSummary.totalSpend shouldBe Money.ZERO
                guest.visitSummary.lastVisitDate shouldBe null
                guest.visitSummary.averageStayNights shouldBe 0.0
            }
        }

        `when`("이름이 공백이면") {
            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    newGuest(name = "  ")
                }
            }
        }

        `when`("전화번호가 공백이면") {
            then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    newGuest(phone = "")
                }
            }
        }
    }

    // ─────────────────────────────────────────
    // VisitSummary.recordVisit()
    // ─────────────────────────────────────────

    given("VisitSummary가 비어있을 때") {
        val summary = VisitSummary.EMPTY

        `when`("방문을 기록하면") {
            val visitDate = LocalDate.of(2026, 3, 13)
            val result = summary.recordVisit(spend = Money.of(100_000), stayNights = 2, visitDate = visitDate)

            then("총 방문 횟수가 1 증가한다") {
                result.totalVisits shouldBe 1
            }
            then("누적 지출이 더해진다") {
                result.totalSpend shouldBe Money.of(100_000)
            }
            then("최근 방문일이 갱신된다") {
                result.lastVisitDate shouldBe visitDate
            }
            then("평균 숙박일수가 계산된다") {
                result.averageStayNights shouldBe 2.0
            }
        }
    }

    given("VisitSummary에 방문 기록이 이미 있을 때") {
        val summary = VisitSummary.EMPTY
            .recordVisit(spend = Money.of(100_000), stayNights = 2, visitDate = LocalDate.of(2026, 1, 1))

        `when`("방문을 추가로 기록하면") {
            val result = summary.recordVisit(
                spend = Money.of(200_000),
                stayNights = 4,
                visitDate = LocalDate.of(2026, 3, 13)
            )

            then("총 방문 횟수가 누적된다") {
                result.totalVisits shouldBe 2
            }
            then("누적 지출이 합산된다") {
                result.totalSpend shouldBe Money.of(300_000)
            }
            then("최근 방문일이 최신으로 갱신된다") {
                result.lastVisitDate shouldBe LocalDate.of(2026, 3, 13)
            }
            then("평균 숙박일수가 재계산된다") {
                result.averageStayNights shouldBe 3.0
            }
        }
    }

    // ─────────────────────────────────────────
    // GuestTier 승급 규칙
    // ─────────────────────────────────────────

    given("Guest가 방문을 기록할 때") {
        `when`("방문 횟수가 0이면") {
            val guest = newGuest()
            then("tier는 NEW이다") {
                guest.tier shouldBe GuestTier.NEW
            }
        }

        `when`("방문 횟수가 2이면") {
            val guest = newGuest().recordVisit(Money.of(10_000), 1, visitDate)
                .recordVisit(Money.of(10_000), 1, visitDate)
            then("tier는 BRONZE로 승급한다") {
                guest.tier shouldBe GuestTier.BRONZE
            }
        }

        `when`("방문 횟수가 5이면") {
            var guest = newGuest()
            repeat(5) { guest = guest.recordVisit(Money.of(10_000), 1, visitDate) }
            then("tier는 SILVER로 승급한다") {
                guest.tier shouldBe GuestTier.SILVER
            }
        }

        `when`("방문 횟수가 10이면") {
            var guest = newGuest()
            repeat(10) { guest = guest.recordVisit(Money.of(10_000), 1, visitDate) }
            then("tier는 GOLD로 승급한다") {
                guest.tier shouldBe GuestTier.GOLD
            }
        }

        `when`("방문 횟수가 20이면") {
            var guest = newGuest()
            repeat(20) { guest = guest.recordVisit(Money.of(10_000), 1, visitDate) }
            then("tier는 VIP로 승급한다") {
                guest.tier shouldBe GuestTier.VIP
            }
        }

        `when`("방문 횟수가 10이지만 누적 지출이 500만원 이상이면") {
            var guest = newGuest()
            repeat(10) { guest = guest.recordVisit(Money.of(500_000), 1, visitDate) }
            then("tier는 VIP로 승급한다") {
                guest.tier shouldBe GuestTier.VIP
            }
        }
    }

    // ─────────────────────────────────────────
    // Guest 정보 수정
    // ─────────────────────────────────────────

    given("Guest 정보를 수정할 때") {
        val guest = newGuest()

        `when`("이름과 메모를 수정하면") {
            val updated = guest.update(name = "김철수", memo = "VIP 고객")
            then("이름과 메모가 변경된다") {
                updated.name shouldBe "김철수"
                updated.memo shouldBe "VIP 고객"
            }
            then("전화번호는 변경되지 않는다") {
                updated.phone shouldBe guest.phone
            }
        }
    }
})
