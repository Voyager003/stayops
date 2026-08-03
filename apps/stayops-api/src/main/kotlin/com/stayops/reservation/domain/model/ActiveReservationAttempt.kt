package com.stayops.reservation.domain.model

import java.time.Instant

class ActiveReservationAttempt private constructor(
    val bookingKey: String,
    val reservationIntentId: String,
    val memberId: String,
    val expiresAt: Instant,
    val createdAt: Instant
) {
    companion object {
        fun create(
            bookingKey: String,
            reservationIntentId: String,
            memberId: String,
            expiresAt: Instant,
            createdAt: Instant
        ): ActiveReservationAttempt {
            require(bookingKey.isNotBlank()) { "bookingKey는 필수입니다" }
            require(reservationIntentId.isNotBlank()) { "reservationIntentId는 필수입니다" }
            require(memberId.isNotBlank()) { "memberId는 필수입니다" }
            require(expiresAt.isAfter(createdAt)) { "만료 시간은 생성 시간 이후여야 합니다" }
            return ActiveReservationAttempt(bookingKey, reservationIntentId, memberId, expiresAt, createdAt)
        }

        fun reconstitute(
            bookingKey: String,
            reservationIntentId: String,
            memberId: String,
            expiresAt: Instant,
            createdAt: Instant
        ): ActiveReservationAttempt = ActiveReservationAttempt(
            bookingKey,
            reservationIntentId,
            memberId,
            expiresAt,
            createdAt
        )
    }
}

class ActiveReservationAttemptAlreadyExistsException : RuntimeException()
