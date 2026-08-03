package com.stayops.reservation.infrastructure.persistence.mongo

import com.mongodb.MongoCommandException
import com.stayops.reservation.domain.model.ActiveReservationAttempt
import com.stayops.reservation.domain.model.ActiveReservationAttemptAlreadyExistsException
import com.stayops.reservation.domain.repository.ActiveReservationAttemptRepository
import com.stayops.reservation.infrastructure.persistence.mongo.dao.ActiveReservationAttemptMongoDao
import com.stayops.reservation.infrastructure.persistence.mongo.document.ActiveReservationAttemptDocument
import com.stayops.shared.config.MongoPersistence
import jakarta.annotation.PostConstruct
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Repository

@MongoPersistence
@Repository
class MongoActiveReservationAttemptRepository(
    private val mongo: ActiveReservationAttemptMongoDao,
    private val mongoTemplate: MongoTemplate
) : ActiveReservationAttemptRepository {
    @PostConstruct
    fun createIndexes() {
        mongoTemplate.indexOps(ActiveReservationAttemptDocument::class.java)
            .createIndex(Index().on("reservationIntentId", Sort.Direction.ASC).unique())
    }

    override fun save(attempt: ActiveReservationAttempt): ActiveReservationAttempt =
        try {
            mongo.insert(ActiveReservationAttemptDocument.from(attempt)).toDomain()
        } catch (_: DuplicateKeyException) {
            throw ActiveReservationAttemptAlreadyExistsException()
        } catch (exception: DataIntegrityViolationException) {
            if (exception.hasMongoWriteConflict()) throw ActiveReservationAttemptAlreadyExistsException()
            throw exception
        }

    override fun findByBookingKey(bookingKey: String): ActiveReservationAttempt? =
        mongo.findById(bookingKey).orElse(null)?.toDomain()

    override fun deleteByReservationIntentId(reservationIntentId: String) {
        mongo.deleteByReservationIntentId(reservationIntentId)
    }

    private fun Throwable.hasMongoWriteConflict(): Boolean =
        generateSequence(this) { it.cause }
            .filterIsInstance<MongoCommandException>()
            .any { it.errorCode == 112 }
}
