package com.stayops.payment.infrastructure.persistence.mongo.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.infrastructure.persistence.mongo.document.PaymentOutboxDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface PaymentOutboxMongoDao : MongoRepository<PaymentOutboxDocument, String> {
    fun findByPaymentIdAndType(paymentId: String, type: PaymentOutboxType): PaymentOutboxDocument?
}
