package com.stayops.payment.domain.model

enum class PaymentOutboxStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    FAILED
}
