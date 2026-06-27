package com.stayops.reservation.domain.model

enum class ReservationIntentStatus {
    PAYMENT_WAITING,
    CONFIRM_REQUESTED,
    RESERVED,
    EXPIRED,
    PAYMENT_FAILED,
    CANCEL_REQUESTED,
    CANCELLED
}
