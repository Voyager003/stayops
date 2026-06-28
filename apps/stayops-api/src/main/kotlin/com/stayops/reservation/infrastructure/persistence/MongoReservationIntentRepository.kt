package com.stayops.reservation.infrastructure.persistence

import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.reservation.infrastructure.persistence.dao.ReservationIntentMongoDao
import com.stayops.shared.config.MongoPersistence
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@MongoPersistence
@Repository
class MongoReservationIntentRepository(
    private val mongo: ReservationIntentMongoDao
) : ReservationIntentRepository {

    override fun save(intent: ReservationIntent): ReservationIntent =
        mongo.save(ReservationIntentDocument.from(intent)).toDomain()

    override fun findById(id: String): ReservationIntent? =
        mongo.findById(id).orElse(null)?.toDomain()

    override fun findExpiredPaymentWaiting(now: Instant, limit: Int): List<ReservationIntent> =
        mongo.findExpiredByStatuses(
            statuses = listOf(ReservationIntentStatus.PAYMENT_WAITING),
            now = now,
            pageable = PageRequest.of(0, limit.coerceAtLeast(1), Sort.by(Sort.Direction.ASC, "expiresAt", "id"))
        ).map { it.toDomain() }

    override fun existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
        memberId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        now: Instant
    ): Boolean =
        mongo.existsActiveByMemberAndStayDates(
            memberId = memberId,
            roomTypeId = roomTypeId,
            checkIn = checkIn.toString(),
            checkOut = checkOut.toString(),
            statuses = activeStatuses,
            expiresAt = now
        )

    companion object {
        private val activeStatuses = listOf(
            ReservationIntentStatus.PAYMENT_WAITING,
            ReservationIntentStatus.CONFIRM_REQUESTED
        )
    }
}
