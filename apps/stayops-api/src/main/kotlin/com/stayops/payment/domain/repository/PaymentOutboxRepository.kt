package com.stayops.payment.domain.repository

import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxType
import java.time.Instant

interface PaymentOutboxRepository {
    fun save(message: PaymentOutboxMessage): PaymentOutboxMessage
    fun findById(id: String): PaymentOutboxMessage?
    fun findByPaymentIdAndType(paymentId: String, type: PaymentOutboxType): PaymentOutboxMessage?
    fun findReadyForProcessing(now: Instant): List<PaymentOutboxMessage>
}
