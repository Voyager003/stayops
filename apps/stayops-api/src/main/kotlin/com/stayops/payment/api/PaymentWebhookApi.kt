package com.stayops.payment.api

import com.stayops.payment.application.service.PaymentStatusChangedWebhookCommand
import com.stayops.payment.application.service.PaymentWebhookApplication
import com.stayops.payment.application.required.PaymentGatewayException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/payments/toss/webhooks")
class PaymentWebhookApi(
    private val paymentWebhookApplication: PaymentWebhookApplication
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun receiveTossWebhook(
        @RequestHeader("tosspayments-webhook-transmission-id", required = false) transmissionId: String?,
        @RequestBody request: TossPaymentWebhookRequest
    ): ResponseEntity<Void> {
        return try {
            paymentWebhookApplication.handleTossPaymentStatusChanged(request.toCommand(transmissionId))
            ResponseEntity.ok().build()
        } catch (e: PaymentGatewayException.ProviderError) {
            log.warn(
                "결제 웹훅 PG 조회 장애로 재전송 요청: transmissionId={}, eventType={}, orderId={}, paymentKeySuffix={}",
                transmissionId,
                request.eventType,
                request.data.orderId,
                request.data.paymentKey.takeLast(4)
            )
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        } catch (e: PaymentGatewayException.UnknownError) {
            log.warn(
                "결제 웹훅 PG 조회 알 수 없는 오류로 재전송 요청: transmissionId={}, eventType={}, orderId={}, paymentKeySuffix={}",
                transmissionId,
                request.eventType,
                request.data.orderId,
                request.data.paymentKey.takeLast(4)
            )
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
    }
}

data class TossPaymentWebhookRequest(
    val eventType: String,
    val createdAt: String?,
    val data: TossPaymentWebhookPaymentData
) {
    fun toCommand(transmissionId: String?): PaymentStatusChangedWebhookCommand = PaymentStatusChangedWebhookCommand(
        eventType = eventType,
        transmissionId = transmissionId,
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
