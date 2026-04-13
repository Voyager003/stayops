package com.stayops.payment.domain.model

enum class PaymentStatus {
    PENDING,
    CONFIRM_REQUESTED,
    APPROVED,
    FAILED,
    CANCEL_REQUESTED,
    CANCELLED,
    CANCEL_FAILED
}
