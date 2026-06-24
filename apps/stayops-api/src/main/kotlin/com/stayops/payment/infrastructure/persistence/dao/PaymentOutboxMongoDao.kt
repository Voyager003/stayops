package com.stayops.payment.infrastructure.persistence.dao

import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.infrastructure.persistence.PaymentOutboxDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface PaymentOutboxMongoDao : MongoRepository<PaymentOutboxDocument, String> {
    fun findByPaymentIdAndType(paymentId: String, type: PaymentOutboxType): PaymentOutboxDocument?
}
