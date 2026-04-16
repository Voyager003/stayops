package com.stayops.payment.domain.service

import java.math.BigDecimal
import java.time.Instant

interface PaymentGateway {
    fun confirm(paymentKey: String, orderId: String, amount: BigDecimal, idempotencyKey: String): PaymentConfirmResult
    fun cancel(paymentKey: String, cancelReason: String, idempotencyKey: String): PaymentCancelResult
    fun inquire(paymentKey: String): PaymentInquiryResult
}

data class PaymentConfirmResult(
    val paymentKey: String,
    val orderId: String,
    val method: String?,
    val approvedAt: Instant?,
    val totalAmount: BigDecimal,
    val receiptUrl: String?,
    val cardNumber: String?,
    val cardCompany: String?
)

data class PaymentCancelResult(
    val paymentKey: String
)

data class PaymentInquiryResult(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: BigDecimal
)
