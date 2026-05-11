package com.stayops.payment.infrastructure.external

import com.stayops.payment.domain.service.PaymentCancelResult
import com.stayops.payment.domain.service.PaymentConfirmResult
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.payment.domain.service.PaymentInquiryResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant

@Component
@ConditionalOnProperty(name = ["stayops.payment.gateway"], havingValue = "loadtest")
class LoadtestPaymentGateway(
    @Value("\${stayops.payment.loadtest-latency-ms:0}") private val latencyMs: Long
) : PaymentGateway {

    override fun confirm(
        paymentKey: String,
        orderId: String,
        amount: BigDecimal,
        idempotencyKey: String
    ): PaymentConfirmResult {
        sleepIfConfigured()
        return PaymentConfirmResult(
            paymentKey = paymentKey,
            orderId = orderId,
            method = "LOADTEST",
            approvedAt = Instant.now(),
            totalAmount = amount,
            receiptUrl = null,
            cardNumber = null,
            cardCompany = "LOADTEST"
        )
    }

    override fun cancel(paymentKey: String, cancelReason: String, idempotencyKey: String): PaymentCancelResult {
        sleepIfConfigured()
        return PaymentCancelResult(paymentKey = paymentKey)
    }

    override fun inquire(paymentKey: String): PaymentInquiryResult {
        sleepIfConfigured()
        return PaymentInquiryResult(
            paymentKey = paymentKey,
            orderId = paymentKey,
            status = "DONE",
            totalAmount = BigDecimal.ZERO
        )
    }

    private fun sleepIfConfigured() {
        if (latencyMs > 0) {
            Thread.sleep(latencyMs)
        }
    }
}
