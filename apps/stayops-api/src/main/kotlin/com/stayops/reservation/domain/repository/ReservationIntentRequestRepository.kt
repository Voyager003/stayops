package com.stayops.reservation.domain.repository

import com.stayops.reservation.domain.model.ReservationIntentRequest

interface ReservationIntentRequestRepository {
    fun save(request: ReservationIntentRequest): ReservationIntentRequest
    fun findByMemberIdAndIdempotencyKey(memberId: String, idempotencyKey: String): ReservationIntentRequest?
}
