package com.stayops.shared.domain

import java.math.BigDecimal
import java.math.RoundingMode

@ConsistentCopyVisibility
data class Money private constructor(
    val amount: BigDecimal,
    val currency: String
) {
    init {
        require(amount >= BigDecimal.ZERO) { "금액은 0 이상이어야 합니다: $amount" }
    }

    fun add(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.add(other.amount), currency)
    }

    fun subtract(other: Money): Money {
        requireSameCurrency(other)
        return Money(amount.subtract(other.amount), currency)
    }

    fun multiply(multiplier: Int): Money {
        return Money(amount.multiply(BigDecimal(multiplier)), currency)
    }

    fun multiply(multiplier: BigDecimal): Money {
        return Money(amount.multiply(multiplier), currency)
    }

    fun percentage(percent: BigDecimal): Money {
        return Money(
            amount.multiply(percent).divide(BigDecimal(100), 0, RoundingMode.HALF_UP),
            currency
        )
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "통화가 일치하지 않습니다: $currency vs ${other.currency}"
        }
    }

    companion object {
        val ZERO: Money = Money(BigDecimal.ZERO, "KRW")

        fun of(amount: BigDecimal, currency: String = "KRW"): Money {
            return Money(amount, currency)
        }

        fun of(amount: Long, currency: String = "KRW"): Money {
            return Money(BigDecimal(amount), currency)
        }

        fun won(amount: Long): Money {
            return Money(BigDecimal(amount), "KRW")
        }

        fun won(amount: BigDecimal): Money {
            return Money(amount, "KRW")
        }
    }
}
