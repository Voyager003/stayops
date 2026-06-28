package com.stayops.reservation.infrastructure.persistence.dao

import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.infrastructure.persistence.ReservationIntentDocument
import com.stayops.shared.config.MongoPersistence
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import java.time.Instant

@MongoPersistence
interface ReservationIntentMongoDao : MongoRepository<ReservationIntentDocument, String> {
    @Query(
        value = "{ 'memberId': ?0, 'roomTypeId': ?1, 'dateRange.checkIn': ?2, 'dateRange.checkOut': ?3, 'status': { '\$in': ?4 }, 'expiresAt': { '\$gte': ?5 } }",
        exists = true
    )
    fun existsActiveByMemberAndStayDates(
        memberId: String,
        roomTypeId: String,
        checkIn: String,
        checkOut: String,
        statuses: Collection<ReservationIntentStatus>,
        expiresAt: Instant
    ): Boolean

    @Query("{ 'status': { '\$in': ?0 }, 'expiresAt': { '\$lt': ?1 } }")
    fun findExpiredByStatuses(
        statuses: Collection<ReservationIntentStatus>,
        now: Instant,
        pageable: Pageable
    ): List<ReservationIntentDocument>
}
