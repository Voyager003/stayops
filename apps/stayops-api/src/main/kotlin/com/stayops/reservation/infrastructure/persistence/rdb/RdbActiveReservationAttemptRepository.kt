package com.stayops.reservation.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.ACTIVE_RESERVATION_ATTEMPTS
import com.stayops.reservation.domain.model.ActiveReservationAttempt
import com.stayops.reservation.domain.model.ActiveReservationAttemptAlreadyExistsException
import com.stayops.reservation.domain.repository.ActiveReservationAttemptRepository
import com.stayops.shared.config.RdbPersistence
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbActiveReservationAttemptRepository(
    private val dsl: DSLContext
) : ActiveReservationAttemptRepository {
    override fun save(attempt: ActiveReservationAttempt): ActiveReservationAttempt {
        val affectedRows = dsl.insertInto(ACTIVE_RESERVATION_ATTEMPTS)
            .set(ACTIVE_RESERVATION_ATTEMPTS.BOOKING_KEY, attempt.bookingKey)
            .set(ACTIVE_RESERVATION_ATTEMPTS.RESERVATION_INTENT_ID, attempt.reservationIntentId)
            .set(ACTIVE_RESERVATION_ATTEMPTS.MEMBER_ID, attempt.memberId)
            .set(ACTIVE_RESERVATION_ATTEMPTS.EXPIRES_AT, attempt.expiresAt.toOffsetDateTime())
            .set(ACTIVE_RESERVATION_ATTEMPTS.CREATED_AT, attempt.createdAt.toOffsetDateTime())
            .onConflict(ACTIVE_RESERVATION_ATTEMPTS.BOOKING_KEY)
            .doNothing()
            .execute()
        if (affectedRows == 0) throw ActiveReservationAttemptAlreadyExistsException()
        return attempt
    }

    override fun findByBookingKey(bookingKey: String): ActiveReservationAttempt? =
        dsl.selectFrom(ACTIVE_RESERVATION_ATTEMPTS)
            .where(ACTIVE_RESERVATION_ATTEMPTS.BOOKING_KEY.eq(bookingKey))
            .fetchOne()
            ?.let {
                ActiveReservationAttempt.reconstitute(
                    bookingKey = it.bookingKey,
                    reservationIntentId = it.reservationIntentId,
                    memberId = it.memberId,
                    expiresAt = it.expiresAt.toInstant(),
                    createdAt = it.createdAt.toInstant()
                )
            }

    override fun deleteByReservationIntentId(reservationIntentId: String) {
        dsl.deleteFrom(ACTIVE_RESERVATION_ATTEMPTS)
            .where(ACTIVE_RESERVATION_ATTEMPTS.RESERVATION_INTENT_ID.eq(reservationIntentId))
            .execute()
    }

    private fun Instant.toOffsetDateTime(): OffsetDateTime = atOffset(ZoneOffset.UTC)
}
