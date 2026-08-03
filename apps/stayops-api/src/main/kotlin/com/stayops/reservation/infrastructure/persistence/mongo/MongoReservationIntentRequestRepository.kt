package com.stayops.reservation.infrastructure.persistence.mongo

import com.stayops.reservation.domain.model.ReservationIntentRequest
import com.stayops.reservation.domain.model.ReservationIntentRequestAlreadyExistsException
import com.stayops.reservation.domain.repository.ReservationIntentRequestRepository
import com.stayops.reservation.infrastructure.persistence.mongo.dao.ReservationIntentRequestMongoDao
import com.stayops.reservation.infrastructure.persistence.mongo.document.ReservationIntentRequestDocument
import com.stayops.shared.config.MongoPersistence
import jakarta.annotation.PostConstruct
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Repository

@MongoPersistence
@Repository
class MongoReservationIntentRequestRepository(
    private val mongo: ReservationIntentRequestMongoDao,
    private val mongoTemplate: MongoTemplate
) : ReservationIntentRequestRepository {
    @PostConstruct
    fun createIndexes() {
        val indexOps = mongoTemplate.indexOps(ReservationIntentRequestDocument::class.java)
        indexOps.createIndex(
            Index()
                .on("memberId", Sort.Direction.ASC)
                .on("idempotencyKey", Sort.Direction.ASC)
                .unique()
        )
        indexOps.createIndex(Index().on("reservationIntentId", Sort.Direction.ASC).unique())
    }

    override fun save(request: ReservationIntentRequest): ReservationIntentRequest =
        try {
            mongo.insert(ReservationIntentRequestDocument.from(request)).toDomain()
        } catch (_: DuplicateKeyException) {
            throw ReservationIntentRequestAlreadyExistsException()
        } catch (exception: DataIntegrityViolationException) {
            if (exception.hasMongoWriteConflict()) {
                throw ReservationIntentRequestAlreadyExistsException()
            }
            throw exception
        }

    override fun findByMemberIdAndIdempotencyKey(
        memberId: String,
        idempotencyKey: String
    ): ReservationIntentRequest? =
        mongo.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey)?.toDomain()

    private fun Throwable.hasMongoWriteConflict(): Boolean =
        generateSequence(this) { it.cause }
            .filterIsInstance<com.mongodb.MongoCommandException>()
            .any { it.errorCode == 112 }
}
