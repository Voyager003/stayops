package com.stayops.payment.api

import com.stayops.payment.application.service.PaymentStatusChangedWebhookCommand
import com.stayops.payment.application.service.PaymentWebhookApplication
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/payments/toss/webhooks")
class PaymentWebhookApi(
    private val paymentWebhookApplication: PaymentWebhookApplication
) {

    @PostMapping
    fun receiveTossWebhook(@RequestBody request: TossPaymentWebhookRequest): ResponseEntity<Void> {
        paymentWebhookApplication.handleTossPaymentStatusChanged(request.toCommand())
        return ResponseEntity.ok().build()
    }
}

data class TossPaymentWebhookRequest(
    val eventType: String,
    val createdAt: String?,
    val data: TossPaymentWebhookPaymentData
) {
    fun toCommand(): PaymentStatusChangedWebhookCommand = PaymentStatusChangedWebhookCommand(
        eventType = eventType,
        paymentKey = data.paymentKey,
        orderId = data.orderId,
        status = data.status,
        totalAmount = data.totalAmount
    )
}

data class TossPaymentWebhookPaymentData(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: BigDecimal
)
