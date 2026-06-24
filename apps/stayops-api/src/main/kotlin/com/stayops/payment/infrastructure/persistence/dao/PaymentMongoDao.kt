package com.stayops.payment.infrastructure.persistence.dao

import com.stayops.payment.infrastructure.persistence.PaymentDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface PaymentMongoDao : MongoRepository<PaymentDocument, String> {
    fun findByReservationId(reservationId: String): PaymentDocument?
    fun findByReservationIdIn(reservationIds: List<String>): List<PaymentDocument>
    fun findByMemberId(memberId: String): List<PaymentDocument>
    fun findByOrderId(orderId: String): PaymentDocument?
}
