package com.stayops.payment.infrastructure.persistence

import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.infrastructure.persistence.dao.PaymentMongoDao
import jakarta.annotation.PostConstruct
import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MongoPaymentRepository(
    private val mongo: PaymentMongoDao,
    private val mongoTemplate: MongoTemplate
) : PaymentRepository {

    @PostConstruct
    fun createIndexes() {
        val indexOps = mongoTemplate.indexOps(PaymentDocument::class.java)
        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("reservationId" to 1)))
        )
        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("memberId" to 1)))
        )
        indexOps.createIndex(
            CompoundIndexDefinition(Document(mapOf("orderId" to 1)))
        )
    }

    override fun save(payment: Payment): Payment =
        mongo.save(PaymentDocument.from(payment)).toDomain()

    override fun findById(id: String): Payment? =
        mongo.findByIdOrNull(id)?.toDomain()

    override fun findByReservationId(reservationId: String): Payment? =
        mongo.findByReservationId(reservationId)?.toDomain()

    override fun findByReservationIds(reservationIds: List<String>): List<Payment> =
        if (reservationIds.isEmpty()) emptyList() else mongo.findByReservationIdIn(reservationIds).map { it.toDomain() }

    override fun findByMemberId(memberId: String): List<Payment> =
        mongo.findByMemberId(memberId).map { it.toDomain() }

    override fun findByOrderId(orderId: String): Payment? =
        mongo.findByOrderId(orderId)?.toDomain()
}
