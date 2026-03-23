package com.stayops.payment.domain.service

import java.math.BigDecimal
import java.time.Instant

interface PaymentGateway {
    fun confirm(paymentKey: String, orderId: String, amount: BigDecimal): PaymentConfirmResult
    fun cancel(paymentKey: String, cancelReason: String): PaymentCancelResult
}

data class PaymentConfirmResult(
    val paymentKey: String,
    val orderId: String,
    val method: String?,
    val approvedAt: Instant?
)

data class PaymentCancelResult(
    val paymentKey: String
)
