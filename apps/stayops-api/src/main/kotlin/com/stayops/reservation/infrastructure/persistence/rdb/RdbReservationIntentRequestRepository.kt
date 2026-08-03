package com.stayops.reservation.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.RESERVATION_INTENT_REQUESTS
import com.stayops.reservation.domain.model.ReservationIntentRequest
import com.stayops.reservation.domain.model.ReservationIntentRequestAlreadyExistsException
import com.stayops.reservation.domain.repository.ReservationIntentRequestRepository
import com.stayops.shared.config.RdbPersistence
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbReservationIntentRequestRepository(
    private val dsl: DSLContext
) : ReservationIntentRequestRepository {
    override fun save(request: ReservationIntentRequest): ReservationIntentRequest {
        val affectedRows = dsl.insertInto(RESERVATION_INTENT_REQUESTS)
            .set(RESERVATION_INTENT_REQUESTS.MEMBER_ID, request.memberId)
            .set(RESERVATION_INTENT_REQUESTS.IDEMPOTENCY_KEY, request.idempotencyKey)
            .set(RESERVATION_INTENT_REQUESTS.REQUEST_HASH, request.requestHash)
            .set(RESERVATION_INTENT_REQUESTS.RESERVATION_INTENT_ID, request.reservationIntentId)
            .set(RESERVATION_INTENT_REQUESTS.CREATED_AT, request.createdAt.toOffsetDateTime())
            .onConflict(RESERVATION_INTENT_REQUESTS.MEMBER_ID, RESERVATION_INTENT_REQUESTS.IDEMPOTENCY_KEY)
            .doNothing()
            .execute()
        if (affectedRows == 0) throw ReservationIntentRequestAlreadyExistsException()
        return request
    }

    override fun findByMemberIdAndIdempotencyKey(
        memberId: String,
        idempotencyKey: String
    ): ReservationIntentRequest? =
        dsl.selectFrom(RESERVATION_INTENT_REQUESTS)
            .where(RESERVATION_INTENT_REQUESTS.MEMBER_ID.eq(memberId))
            .and(RESERVATION_INTENT_REQUESTS.IDEMPOTENCY_KEY.eq(idempotencyKey))
            .fetchOne()
            ?.let {
                ReservationIntentRequest.create(
                    memberId = it.memberId,
                    idempotencyKey = it.idempotencyKey,
                    requestHash = it.requestHash,
                    reservationIntentId = it.reservationIntentId,
                    createdAt = it.createdAt.toInstant()
                )
            }

    private fun Instant.toOffsetDateTime(): OffsetDateTime = atOffset(ZoneOffset.UTC)
}
