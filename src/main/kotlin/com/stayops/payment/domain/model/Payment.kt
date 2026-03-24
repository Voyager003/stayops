package com.stayops.payment.domain.model

import com.stayops.shared.domain.Money
import java.time.Instant

@ConsistentCopyVisibility
data class Payment private constructor(
    val id: String,
    val reservationId: String,
    val memberId: String,
    val orderId: String,
    val amount: Money,
    val status: PaymentStatus,
    val paymentKey: String?,
    val method: String?,
    val failReason: String?,
    val approvedAt: Instant?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    fun approve(paymentKey: String, method: String, approvedAt: Instant): Payment {
        check(status == PaymentStatus.PENDING) {
            "PENDING 상태에서만 승인할 수 있습니다: $status"
        }
        return copy(
            status = PaymentStatus.APPROVED,
            paymentKey = paymentKey,
            method = method,
            approvedAt = approvedAt,
            updatedAt = Instant.now()
        )
    }

    fun fail(reason: String): Payment {
        check(status == PaymentStatus.PENDING) {
            "PENDING 상태에서만 실패 처리할 수 있습니다: $status"
        }
        return copy(
            status = PaymentStatus.FAILED,
            failReason = reason,
            updatedAt = Instant.now()
        )
    }

    fun cancel(): Payment {
        check(status == PaymentStatus.APPROVED) {
            "APPROVED 상태에서만 취소할 수 있습니다: $status"
        }
        return copy(
            status = PaymentStatus.CANCELLED,
            updatedAt = Instant.now()
        )
    }

    fun failCancel(reason: String): Payment {
        check(status == PaymentStatus.APPROVED) {
            "APPROVED 상태에서만 환불 실패 처리할 수 있습니다: $status"
        }
        return copy(
            status = PaymentStatus.CANCEL_FAILED,
            failReason = reason,
            updatedAt = Instant.now()
        )
    }

    companion object {
        fun create(
            id: String,
            reservationId: String,
            memberId: String,
            amount: Money
        ): Payment {
            require(reservationId.isNotBlank()) { "reservationId는 필수입니다" }
            require(memberId.isNotBlank()) { "memberId는 필수입니다" }

            val now = Instant.now()
            val orderId = "STAYOPS-$reservationId-${now.toEpochMilli()}"

            return Payment(
                id = id,
                reservationId = reservationId,
                memberId = memberId,
                orderId = orderId,
                amount = amount,
                status = PaymentStatus.PENDING,
                paymentKey = null,
                method = null,
                failReason = null,
                approvedAt = null,
                version = 0L,
                createdAt = now,
                updatedAt = now
            )
        }

        fun reconstitute(
            id: String,
            reservationId: String,
            memberId: String,
            orderId: String,
            amount: Money,
            status: PaymentStatus,
            paymentKey: String?,
            method: String?,
            failReason: String?,
            approvedAt: Instant?,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): Payment = Payment(
            id = id,
            reservationId = reservationId,
            memberId = memberId,
            orderId = orderId,
            amount = amount,
            status = status,
            paymentKey = paymentKey,
            method = method,
            failReason = failReason,
            approvedAt = approvedAt,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
