package com.stayops.reservation.infrastructure.persistence.mongo.document

import com.stayops.reservation.domain.model.ReservationIntentRequest
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("reservation_intent_requests")
data class ReservationIntentRequestDocument(
    @Id val id: String,
    val memberId: String,
    val idempotencyKey: String,
    val requestHash: String,
    val reservationIntentId: String,
    val createdAt: Instant
) {
    fun toDomain(): ReservationIntentRequest = ReservationIntentRequest.create(
        memberId = memberId,
        idempotencyKey = idempotencyKey,
        requestHash = requestHash,
        reservationIntentId = reservationIntentId,
        createdAt = createdAt
    )

    companion object {
        fun from(request: ReservationIntentRequest): ReservationIntentRequestDocument =
            ReservationIntentRequestDocument(
                id = "${request.memberId}:${request.idempotencyKey}",
                memberId = request.memberId,
                idempotencyKey = request.idempotencyKey,
                requestHash = request.requestHash,
                reservationIntentId = request.reservationIntentId,
                createdAt = request.createdAt
            )
    }
}
