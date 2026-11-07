package com.stayops.reservation.infrastructure.persistence.mongo.document

import com.stayops.reservation.domain.model.ActiveReservationAttempt
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("active_reservation_attempts")
data class ActiveReservationAttemptDocument(
    @Id val bookingKey: String,
    val reservationIntentId: String,
    val memberId: String,
    val expiresAt: Instant,
    val createdAt: Instant
) {
    fun toDomain(): ActiveReservationAttempt = ActiveReservationAttempt.reconstitute(
        bookingKey = bookingKey,
        reservationIntentId = reservationIntentId,
        memberId = memberId,
        expiresAt = expiresAt,
        createdAt = createdAt
    )

    companion object {
        fun from(attempt: ActiveReservationAttempt): ActiveReservationAttemptDocument =
            ActiveReservationAttemptDocument(
                bookingKey = attempt.bookingKey,
                reservationIntentId = attempt.reservationIntentId,
                memberId = attempt.memberId,
                expiresAt = attempt.expiresAt,
                createdAt = attempt.createdAt
            )
    }
}
