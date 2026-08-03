package com.stayops.reservation.infrastructure.persistence.mongo.dao

import com.stayops.reservation.infrastructure.persistence.mongo.document.ReservationIntentRequestDocument
import com.stayops.shared.config.MongoPersistence
import org.springframework.data.mongodb.repository.MongoRepository

@MongoPersistence
interface ReservationIntentRequestMongoDao : MongoRepository<ReservationIntentRequestDocument, String> {
    fun findByMemberIdAndIdempotencyKey(memberId: String, idempotencyKey: String): ReservationIntentRequestDocument?
}
