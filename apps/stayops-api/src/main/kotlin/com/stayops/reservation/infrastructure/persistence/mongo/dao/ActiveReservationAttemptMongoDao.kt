package com.stayops.reservation.infrastructure.persistence.mongo.dao

import com.stayops.reservation.infrastructure.persistence.mongo.document.ActiveReservationAttemptDocument
import com.stayops.shared.config.MongoPersistence
import org.springframework.data.mongodb.repository.MongoRepository

@MongoPersistence
interface ActiveReservationAttemptMongoDao : MongoRepository<ActiveReservationAttemptDocument, String> {
    fun deleteByReservationIntentId(reservationIntentId: String)
}
