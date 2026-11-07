package com.stayops.reservation.domain.model

import java.time.Instant

@ConsistentCopyVisibility
data class ReservationIntentRequest private constructor(
    val memberId: String,
    val idempotencyKey: String,
    val requestHash: String,
    val reservationIntentId: String,
    val createdAt: Instant
) {
    companion object {
        fun create(
            memberId: String,
            idempotencyKey: String,
            requestHash: String,
            reservationIntentId: String,
            createdAt: Instant
        ): ReservationIntentRequest = ReservationIntentRequest(
            memberId = memberId,
            idempotencyKey = idempotencyKey,
            requestHash = requestHash,
            reservationIntentId = reservationIntentId,
            createdAt = createdAt
        )
    }
}

class ReservationIntentRequestAlreadyExistsException : RuntimeException()
