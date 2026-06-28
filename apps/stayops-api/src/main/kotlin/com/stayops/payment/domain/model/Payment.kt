package com.stayops.payment.domain.model

import com.stayops.shared.domain.Money
import java.time.Instant

class Payment private constructor(
    val id: String,
    val reservationId: String?,
    val reservationIntentId: String?,
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

    fun requestConfirm(paymentKey: String): Payment {
        require(paymentKey.isNotBlank()) { "paymentKey는 필수입니다" }

        if (status == PaymentStatus.CONFIRM_REQUESTED) {
            check(this.paymentKey == paymentKey) {
                "이미 다른 paymentKey로 승인 요청되었습니다: ${this.paymentKey}"
            }
            return this
        }

        check(status == PaymentStatus.PENDING) {
            "PENDING 상태에서만 승인 요청할 수 있습니다: $status"
        }

        return copyState(
            status = PaymentStatus.CONFIRM_REQUESTED,
            paymentKey = paymentKey,
            failReason = null
        )
    }

    fun approve(paymentKey: String, method: String, approvedAt: Instant): Payment {
        require(paymentKey.isNotBlank()) { "paymentKey는 필수입니다" }
        check(status == PaymentStatus.PENDING || status == PaymentStatus.CONFIRM_REQUESTED) {
            "PENDING 또는 CONFIRM_REQUESTED 상태에서만 승인할 수 있습니다: $status"
        }
        check(this.paymentKey == null || this.paymentKey == paymentKey) {
            "승인 요청 paymentKey와 승인 결과 paymentKey가 다릅니다: ${this.paymentKey}, $paymentKey"
        }
        return copyState(
            status = PaymentStatus.APPROVED,
            paymentKey = paymentKey,
            method = method,
            approvedAt = approvedAt
        )
    }

    fun fail(reason: String): Payment {
        check(status == PaymentStatus.PENDING || status == PaymentStatus.CONFIRM_REQUESTED) {
            "PENDING 또는 CONFIRM_REQUESTED 상태에서만 실패 처리할 수 있습니다: $status"
        }
        return copyState(
            status = PaymentStatus.FAILED,
            failReason = reason
        )
    }

    fun requestCancel(): Payment {
        if (status == PaymentStatus.CANCEL_REQUESTED) {
            return this
        }

        check(status == PaymentStatus.APPROVED) {
            "APPROVED 상태에서만 취소 요청할 수 있습니다: $status"
        }
        check(!paymentKey.isNullOrBlank()) {
            "paymentKey가 있어야 취소 요청할 수 있습니다"
        }

        return copyState(
            status = PaymentStatus.CANCEL_REQUESTED,
            failReason = null
        )
    }

    fun cancel(): Payment {
        check(status == PaymentStatus.APPROVED || status == PaymentStatus.CANCEL_REQUESTED) {
            "APPROVED 또는 CANCEL_REQUESTED 상태에서만 취소할 수 있습니다: $status"
        }
        return copyState(status = PaymentStatus.CANCELLED)
    }

    fun failCancel(reason: String): Payment {
        check(status == PaymentStatus.APPROVED || status == PaymentStatus.CANCEL_REQUESTED) {
            "APPROVED 또는 CANCEL_REQUESTED 상태에서만 환불 실패 처리할 수 있습니다: $status"
        }
        return copyState(
            status = PaymentStatus.CANCEL_FAILED,
            failReason = reason
        )
    }

    fun requireReservationId(): String = checkNotNull(reservationId) {
        "예약 확정 결제에는 reservationId가 필요합니다"
    }

    fun attachReservation(reservationId: String): Payment {
        require(reservationId.isNotBlank()) { "reservationId는 필수입니다" }
        check(this.reservationId == null || this.reservationId == reservationId) {
            "이미 다른 예약에 연결된 결제입니다: ${this.reservationId}"
        }
        return copyState(reservationId = reservationId)
    }

    private fun copyState(
        reservationId: String? = this.reservationId,
        status: PaymentStatus = this.status,
        paymentKey: String? = this.paymentKey,
        method: String? = this.method,
        failReason: String? = this.failReason,
        approvedAt: Instant? = this.approvedAt,
        updatedAt: Instant = Instant.now()
    ): Payment = Payment(
        id = id,
        reservationId = reservationId,
        reservationIntentId = reservationIntentId,
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Payment

        return id == other.id
    }

    override fun hashCode(): Int = 31 * javaClass.hashCode() + id.hashCode()

    override fun toString(): String = "Payment(id=$id, status=$status, orderId=$orderId)"

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
                reservationIntentId = null,
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

        fun createForReservationIntent(
            id: String,
            reservationIntentId: String,
            memberId: String,
            amount: Money
        ): Payment {
            require(reservationIntentId.isNotBlank()) { "reservationIntentId는 필수입니다" }
            require(memberId.isNotBlank()) { "memberId는 필수입니다" }

            val now = Instant.now()
            val orderId = "STAYOPS-$reservationIntentId-${now.toEpochMilli()}"

            return Payment(
                id = id,
                reservationId = null,
                reservationIntentId = reservationIntentId,
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
            reservationId: String?,
            reservationIntentId: String? = null,
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
            reservationIntentId = reservationIntentId,
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
