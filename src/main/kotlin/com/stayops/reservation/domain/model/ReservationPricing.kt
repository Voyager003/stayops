package com.stayops.reservation.domain.model

import com.stayops.shared.domain.Money
import java.math.BigDecimal
import java.math.RoundingMode

data class ReservationPricing(
    val roomRate: Money,
    val additionalCharges: Money,
    val totalAmount: Money,
    val commissionAmount: Money,
    val netAmount: Money
) {
    companion object {
        fun calculate(
            roomRate: Money,
            additionalCharges: Money,
            commissionRate: BigDecimal
        ): ReservationPricing {
            val totalAmount = roomRate.add(additionalCharges)
            val commissionAmount = Money.of(
                totalAmount.amount.multiply(commissionRate).setScale(0, RoundingMode.HALF_UP),
                totalAmount.currency
            )
            val netAmount = totalAmount.subtract(commissionAmount)

            return ReservationPricing(
                roomRate = roomRate,
                additionalCharges = additionalCharges,
                totalAmount = totalAmount,
                commissionAmount = commissionAmount,
                netAmount = netAmount
            )
        }
    }
}
