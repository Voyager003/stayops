package com.stayops.reservation.application.required

import com.stayops.shared.domain.Money
import java.math.BigDecimal

interface ReservationPaymentService {
    fun createPendingPayment(
        reservationId: String,
        memberId: String,
        amount: Money
    ): ReservationPaymentSnapshot

    fun createPendingPaymentForReservationIntent(
        paymentId: String,
        reservationIntentId: String,
        memberId: String,
        amount: Money
    ): ReservationPaymentSnapshot

    fun createApprovedExternalPayment(
        reservationId: String,
        memberId: String,
        amount: Money,
        paymentKey: String,
        method: String
    ): ReservationPaymentSnapshot

    fun findByReservationId(reservationId: String): ReservationPaymentSnapshot?

    fun findByReservationIntentId(reservationIntentId: String): ReservationPaymentSnapshot?

    fun findByReservationIds(reservationIds: List<String>): List<ReservationPaymentSnapshot>

    fun findByMemberId(memberId: String): List<ReservationPaymentSnapshot>

    fun requestConfirm(
        reservationId: String,
        memberId: String,
        paymentKey: String,
        orderId: String,
        amount: BigDecimal
    ): ReservationPaymentSnapshot

    fun requestConfirmForReservationIntent(
        reservationIntentId: String,
        memberId: String,
        paymentKey: String,
        orderId: String,
        amount: BigDecimal
    ): ReservationPaymentSnapshot

    fun cancelPendingByCustomerRequest(reservationId: String): ReservationPaymentSnapshot

    fun requestCancelByCustomerRequest(
        reservationId: String,
        memberId: String
    ): ReservationPaymentSnapshot
}

data class ReservationPaymentSnapshot(
    val id: String,
    val reservationId: String?,
    val reservationIntentId: String? = null,
    val memberId: String,
    val orderId: String,
    val amount: Money,
    val status: ReservationPaymentStatus,
    val paymentKey: String?,
    val failReason: String?
)

enum class ReservationPaymentStatus {
    PENDING,
    CONFIRM_REQUESTED,
    APPROVED,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED,
    CANCEL_FAILED
}
