package com.stayops.payment.infrastructure.external

import com.stayops.payment.domain.service.PaymentCancelResult
import com.stayops.payment.domain.service.PaymentConfirmResult
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.payment.domain.service.PaymentInquiryResult
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.OffsetDateTime

@Component
class TossPaymentsClient(
    private val restClient: RestClient
) : PaymentGateway {

    override fun confirm(paymentKey: String, orderId: String, amount: BigDecimal): PaymentConfirmResult {
        val response = restClient.post()
            .uri("/confirm")
            .body(TossConfirmRequest(paymentKey, orderId, amount))
            .retrieve()
            .body(TossConfirmResponse::class.java)
            ?: throw IllegalStateException("Toss confirm 응답이 null입니다")

        return PaymentConfirmResult(
            paymentKey = response.paymentKey,
            orderId = response.orderId,
            method = response.method,
            approvedAt = response.approvedAt?.let { OffsetDateTime.parse(it).toInstant() },
            totalAmount = response.totalAmount,
            receiptUrl = response.receipt?.url,
            cardNumber = response.card?.number,
            cardCompany = response.card?.company
        )
    }

    override fun cancel(paymentKey: String, cancelReason: String): PaymentCancelResult {
        val response = restClient.post()
            .uri("/$paymentKey/cancel")
            .body(TossCancelRequest(cancelReason))
            .retrieve()
            .body(TossCancelResponse::class.java)
            ?: throw IllegalStateException("Toss cancel 응답이 null입니다")

        return PaymentCancelResult(paymentKey = response.paymentKey)
    }

    override fun inquire(paymentKey: String): PaymentInquiryResult {
        val response = restClient.get()
            .uri("/$paymentKey")
            .retrieve()
            .body(TossInquiryResponse::class.java)
            ?: throw IllegalStateException("Toss inquire 응답이 null입니다")

        return PaymentInquiryResult(
            paymentKey = response.paymentKey,
            orderId = response.orderId,
            status = response.status,
            totalAmount = response.totalAmount
        )
    }
}

data class TossConfirmRequest(
    val paymentKey: String,
    val orderId: String,
    val amount: BigDecimal
)

data class TossConfirmResponse(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val method: String?,
    val totalAmount: BigDecimal,
    val approvedAt: String?,
    val receipt: TossReceipt? = null,
    val card: TossCard? = null
)

data class TossReceipt(
    val url: String?
)

data class TossCard(
    val number: String?,
    val company: String?
)

data class TossCancelRequest(
    val cancelReason: String
)

data class TossCancelResponse(
    val paymentKey: String,
    val status: String
)

data class TossInquiryResponse(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: BigDecimal
)
