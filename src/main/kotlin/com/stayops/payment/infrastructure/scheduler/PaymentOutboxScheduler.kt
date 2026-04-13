package com.stayops.payment.infrastructure.scheduler

import com.stayops.payment.application.service.PaymentOutboxProcessor
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentOutboxScheduler(
    private val paymentOutboxProcessor: PaymentOutboxProcessor
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${stayops.payment-outbox.fixed-delay-ms:30000}",
        initialDelayString = "\${stayops.payment-outbox.initial-delay-ms:60000}"
    )
    fun processPaymentOutbox() {
        try {
            paymentOutboxProcessor.processPendingMessages()
        } catch (e: Exception) {
            log.error("PaymentOutbox scheduler 처리 실패", e)
        }
    }
}
