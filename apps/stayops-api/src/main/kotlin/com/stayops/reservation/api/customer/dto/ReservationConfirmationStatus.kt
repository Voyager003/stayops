package com.stayops.reservation.api.customer.dto

import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.ReservationStatus

enum class ReservationConfirmationStatus {
    PAYMENT_WAITING,
    CONFIRMING,
    PAYMENT_COMPLETED,
    CONFIRMED,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED,
    MANUAL_REVIEW_REQUIRED;

    companion object {
        fun from(
            reservationStatus: ReservationStatus,
            paymentStatus: ReservationPaymentStatus?
        ): ReservationConfirmationStatus {
            if (reservationStatus == ReservationStatus.CANCELLED) {
                return when (paymentStatus) {
                    ReservationPaymentStatus.CANCEL_REQUESTED -> CANCEL_REQUESTED
                    ReservationPaymentStatus.CANCEL_FAILED -> MANUAL_REVIEW_REQUIRED
                    else -> CANCELLED
                }
            }

            return when (paymentStatus) {
                null, ReservationPaymentStatus.PENDING -> when (reservationStatus) {
                    ReservationStatus.CONFIRMED,
                    ReservationStatus.CHECKED_IN,
                    ReservationStatus.CHECKED_OUT,
                    ReservationStatus.NO_SHOW -> CONFIRMED
                    else -> PAYMENT_WAITING
                }
                ReservationPaymentStatus.CONFIRM_REQUESTED -> CONFIRMING
                ReservationPaymentStatus.APPROVED -> when (reservationStatus) {
                    ReservationStatus.CONFIRMED,
                    ReservationStatus.CHECKED_IN,
                    ReservationStatus.CHECKED_OUT,
                    ReservationStatus.NO_SHOW -> CONFIRMED
                    else -> PAYMENT_COMPLETED
                }
                ReservationPaymentStatus.FAILED -> FAILED
                ReservationPaymentStatus.CANCEL_REQUESTED -> CANCEL_REQUESTED
                ReservationPaymentStatus.CANCELLED -> CANCELLED
                ReservationPaymentStatus.CANCEL_FAILED -> MANUAL_REVIEW_REQUIRED
            }
        }
    }
}
