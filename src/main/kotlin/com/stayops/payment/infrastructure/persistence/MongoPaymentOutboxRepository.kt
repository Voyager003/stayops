package com.stayops.payment.infrastructure.persistence

import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.shared.exception.ConflictException
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class MongoPaymentOutboxRepository(
    private val mongo: PaymentOutboxMongoDataRepository,
    private val mongoTemplate: MongoTemplate
) : PaymentOutboxRepository {

    @PostConstruct
    fun createIndexes() {
        val indexOps = mongoTemplate.indexOps(PaymentOutboxDocument::class.java)

        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("status" to 1, "nextRetryAt" to 1)))
        )
        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("status" to 1, "lockedUntil" to 1)))
        )
        indexOps.createIndex(
            Index()
                .on("paymentId", org.springframework.data.domain.Sort.Direction.ASC)
                .on("type", org.springframework.data.domain.Sort.Direction.ASC)
                .unique()
        )
    }

    override fun save(message: PaymentOutboxMessage): PaymentOutboxMessage =
        try {
            mongo.save(PaymentOutboxDocument.from(message)).toDomain()
        } catch (e: OptimisticLockingFailureException) {
            throw ConflictException(
                code = "PAYMENT_OUTBOX_CONFLICT",
                message = "PaymentOutbox 버전 충돌이 발생했습니다: ${message.id}"
            )
        }

    override fun findById(id: String): PaymentOutboxMessage? =
        mongo.findByIdOrNull(id)?.toDomain()

    override fun findByPaymentIdAndType(paymentId: String, type: PaymentOutboxType): PaymentOutboxMessage? =
        mongo.findByPaymentIdAndType(paymentId, type)?.toDomain()

    override fun findReadyForProcessing(now: Instant): List<PaymentOutboxMessage> {
        val pending = Criteria.where("status").`is`(PaymentOutboxStatus.PENDING)
            .andOperator(
                Criteria().orOperator(
                    Criteria.where("nextRetryAt").`is`(null),
                    Criteria.where("nextRetryAt").lte(now)
                )
            )
        val expiredLease = Criteria.where("status").`is`(PaymentOutboxStatus.IN_PROGRESS)
            .and("lockedUntil").lte(now)

        val query = Query(Criteria().orOperator(pending, expiredLease))
        return mongoTemplate.find(query, PaymentOutboxDocument::class.java).map { it.toDomain() }
    }
}

interface PaymentOutboxMongoDataRepository : MongoRepository<PaymentOutboxDocument, String> {
    fun findByPaymentIdAndType(paymentId: String, type: PaymentOutboxType): PaymentOutboxDocument?
}
