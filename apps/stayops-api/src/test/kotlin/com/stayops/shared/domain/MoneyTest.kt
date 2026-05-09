package com.stayops.shared.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class MoneyTest : BehaviorSpec({

    given("Money 생성") {
        `when`("음수 금액으로 생성하면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Money.of(BigDecimal(-1000))
                }
            }
        }

        `when`("0원으로 생성하면") {
            then("정상적으로 생성된다") {
                val money = Money.of(BigDecimal.ZERO)
                money.amount shouldBe BigDecimal.ZERO
                money.currency shouldBe "KRW"
            }
        }

        `when`("양수 금액으로 생성하면") {
            then("정상적으로 생성된다") {
                val money = Money.won(10000)
                money.amount shouldBe BigDecimal(10000)
                money.currency shouldBe "KRW"
            }
        }
    }

    given("Money 연산") {
        val money1 = Money.won(10000)
        val money2 = Money.won(5000)

        `when`("두 금액을 더하면") {
            val result = money1.add(money2)

            then("합산 금액이 반환된다") {
                result.amount shouldBe BigDecimal(15000)
            }
        }

        `when`("큰 금액에서 작은 금액을 빼면") {
            val result = money1.subtract(money2)

            then("차액이 반환된다") {
                result.amount shouldBe BigDecimal(5000)
            }
        }

        `when`("작은 금액에서 큰 금액을 빼면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    money2.subtract(money1)
                }
            }
        }

        `when`("금액에 배수를 곱하면") {
            val result = money1.multiply(3)

            then("곱한 금액이 반환된다") {
                result.amount shouldBe BigDecimal(30000)
            }
        }

        `when`("퍼센트를 계산하면") {
            val result = money1.percentage(BigDecimal(10))

            then("해당 비율의 금액이 반환된다") {
                result.amount shouldBe BigDecimal(1000)
            }
        }
    }

    given("서로 다른 통화의 Money") {
        val krw = Money.won(10000)
        val usd = Money.of(BigDecimal(100), "USD")

        `when`("더하면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    krw.add(usd)
                }
            }
        }
    }

    given("Money.ZERO") {
        `when`("ZERO 상수를 참조하면") {
            then("0원 KRW이다") {
                Money.ZERO.amount shouldBe BigDecimal.ZERO
                Money.ZERO.currency shouldBe "KRW"
            }
        }
    }
})
