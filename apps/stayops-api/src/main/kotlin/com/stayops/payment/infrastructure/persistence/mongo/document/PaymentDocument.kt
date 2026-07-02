package com.stayops.payment.infrastructure.persistence.mongo.document

import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.shared.domain.Money
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

@Document("payments")
data class PaymentDocument(
    @Id val id: String,
    val reservationId: String?,
    val reservationIntentId: String?,
    val memberId: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val status: PaymentStatus,
    val paymentKey: String?,
    val method: String?,
    val failReason: String?,
    val approvedAt: Instant?,
    @Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    fun toDomain(): Payment = Payment.reconstitute(
        id = id,
        reservationId = reservationId,
        reservationIntentId = reservationIntentId,
        memberId = memberId,
        orderId = orderId,
        amount = Money.of(amount, currency),
        status = status,
        paymentKey = paymentKey,
        method = method,
        failReason = failReason,
        approvedAt = approvedAt,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(payment: Payment): PaymentDocument = PaymentDocument(
            id = payment.id,
            reservationId = payment.reservationId,
            reservationIntentId = payment.reservationIntentId,
            memberId = payment.memberId,
            orderId = payment.orderId,
            amount = payment.amount.amount,
            currency = payment.amount.currency,
            status = payment.status,
            paymentKey = payment.paymentKey,
            method = payment.method,
            failReason = payment.failReason,
            approvedAt = payment.approvedAt,
            version = payment.version,
            createdAt = payment.createdAt,
            updatedAt = payment.updatedAt
        )
    }
}
