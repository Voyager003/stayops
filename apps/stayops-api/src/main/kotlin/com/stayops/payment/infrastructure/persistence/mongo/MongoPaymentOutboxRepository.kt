package com.stayops.payment.infrastructure.persistence.mongo

import com.stayops.shared.config.MongoPersistence

import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.infrastructure.persistence.mongo.dao.PaymentOutboxMongoDao
import com.stayops.payment.infrastructure.persistence.mongo.document.PaymentOutboxDocument
import com.stayops.shared.exception.ConflictException
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.Instant

@MongoPersistence
@Repository
class MongoPaymentOutboxRepository(
    private val mongo: PaymentOutboxMongoDao,
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

    override fun claimReadyForProcessing(
        workerId: String,
        now: Instant,
        lockedUntil: Instant
    ): PaymentOutboxMessage? {
        val query = Query(readyCriteria(now))
            .with(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("_id")))
        val update = Update()
            .set("status", PaymentOutboxStatus.IN_PROGRESS)
            .set("lockedBy", workerId)
            .set("lockedUntil", lockedUntil)
            .set("updatedAt", now)
            .inc("version", 1L)

        return mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            PaymentOutboxDocument::class.java
        )?.toDomain()
    }

    override fun findReadyForProcessing(now: Instant): List<PaymentOutboxMessage> {
        val query = Query(readyCriteria(now))
        return mongoTemplate.find(query, PaymentOutboxDocument::class.java).map { it.toDomain() }
    }

    private fun readyCriteria(now: Instant): Criteria {
        val pending = Criteria.where("status").`is`(PaymentOutboxStatus.PENDING)
            .andOperator(
                Criteria().orOperator(
                    Criteria.where("nextRetryAt").`is`(null),
                    Criteria.where("nextRetryAt").lte(now)
                )
            )
        val expiredLease = Criteria.where("status").`is`(PaymentOutboxStatus.IN_PROGRESS)
            .and("lockedUntil").lte(now)

        return Criteria().orOperator(pending, expiredLease)
    }
}
