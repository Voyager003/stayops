package com.stayops.payment.infrastructure.persistence.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.payment.infrastructure.persistence.PaymentDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface PaymentMongoDao : MongoRepository<PaymentDocument, String> {
    fun findByReservationId(reservationId: String): PaymentDocument?
    fun findByReservationIntentId(reservationIntentId: String): PaymentDocument?
    fun findByReservationIdIn(reservationIds: List<String>): List<PaymentDocument>
    fun findByMemberId(memberId: String): List<PaymentDocument>
    fun findByOrderId(orderId: String): PaymentDocument?
}
