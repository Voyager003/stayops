package com.stayops.reservation.api.customer.dto

import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.reservation.domain.model.ReservationStatus

enum class ReservationConfirmationStatus {
    PAYMENT_WAITING,
    CONFIRMING,
    CONFIRMED,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED,
    MANUAL_REVIEW_REQUIRED;

    companion object {
        fun from(
            reservationStatus: ReservationStatus,
            paymentStatus: PaymentStatus?
        ): ReservationConfirmationStatus {
            if (reservationStatus == ReservationStatus.CANCELLED) {
                return when (paymentStatus) {
                    PaymentStatus.CANCEL_REQUESTED -> CANCEL_REQUESTED
                    PaymentStatus.CANCEL_FAILED -> MANUAL_REVIEW_REQUIRED
                    else -> CANCELLED
                }
            }

            return when (paymentStatus) {
                null, PaymentStatus.PENDING -> when (reservationStatus) {
                    ReservationStatus.CONFIRMED,
                    ReservationStatus.CHECKED_IN,
                    ReservationStatus.CHECKED_OUT,
                    ReservationStatus.NO_SHOW -> CONFIRMED
                    else -> PAYMENT_WAITING
                }
                PaymentStatus.CONFIRM_REQUESTED -> CONFIRMING
                PaymentStatus.APPROVED -> when (reservationStatus) {
                    ReservationStatus.CONFIRMED,
                    ReservationStatus.CHECKED_IN,
                    ReservationStatus.CHECKED_OUT,
                    ReservationStatus.NO_SHOW -> CONFIRMED
                    else -> CONFIRMING
                }
                PaymentStatus.FAILED -> FAILED
                PaymentStatus.CANCEL_REQUESTED -> CANCEL_REQUESTED
                PaymentStatus.CANCELLED -> CANCELLED
                PaymentStatus.CANCEL_FAILED -> MANUAL_REVIEW_REQUIRED
            }
        }
    }
}
