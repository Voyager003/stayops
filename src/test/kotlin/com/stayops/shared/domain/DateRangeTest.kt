package com.stayops.shared.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class DateRangeTest : BehaviorSpec({

    given("DateRange 생성") {
        `when`("체크아웃이 체크인보다 이후이면") {
            val range = DateRange.of(
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 13)
            )

            then("정상적으로 생성된다") {
                range.checkIn shouldBe LocalDate.of(2026, 3, 10)
                range.checkOut shouldBe LocalDate.of(2026, 3, 13)
            }
        }

        `when`("체크아웃이 체크인과 같으면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    DateRange.of(
                        LocalDate.of(2026, 3, 10),
                        LocalDate.of(2026, 3, 10)
                    )
                }
            }
        }

        `when`("체크아웃이 체크인보다 이전이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    DateRange.of(
                        LocalDate.of(2026, 3, 13),
                        LocalDate.of(2026, 3, 10)
                    )
                }
            }
        }
    }

    given("3박 투숙의 DateRange") {
        val range = DateRange.of(
            LocalDate.of(2026, 3, 10),
            LocalDate.of(2026, 3, 13)
        )

        `when`("박 수를 계산하면") {
            then("3이 반환된다") {
                range.nights() shouldBe 3
            }
        }

        `when`("전체 날짜 목록을 조회하면") {
            then("체크인부터 체크아웃 전날까지 반환된다") {
                range.allDates() shouldBe listOf(
                    LocalDate.of(2026, 3, 10),
                    LocalDate.of(2026, 3, 11),
                    LocalDate.of(2026, 3, 12)
                )
            }
        }
    }

    given("두 DateRange의 겹침 여부") {
        val range = DateRange.of(
            LocalDate.of(2026, 3, 10),
            LocalDate.of(2026, 3, 13)
        )

        `when`("기간이 겹치면") {
            val overlapping = DateRange.of(
                LocalDate.of(2026, 3, 12),
                LocalDate.of(2026, 3, 15)
            )

            then("true를 반환한다") {
                range.overlaps(overlapping) shouldBe true
            }
        }

        `when`("기간이 겹치지 않으면 (연속된 날짜)") {
            val nonOverlapping = DateRange.of(
                LocalDate.of(2026, 3, 13),
                LocalDate.of(2026, 3, 16)
            )

            then("false를 반환한다") {
                range.overlaps(nonOverlapping) shouldBe false
            }
        }

        `when`("한쪽이 다른 쪽을 완전히 포함하면") {
            val containing = DateRange.of(
                LocalDate.of(2026, 3, 9),
                LocalDate.of(2026, 3, 14)
            )

            then("true를 반환한다") {
                range.overlaps(containing) shouldBe true
            }
        }
    }
})
