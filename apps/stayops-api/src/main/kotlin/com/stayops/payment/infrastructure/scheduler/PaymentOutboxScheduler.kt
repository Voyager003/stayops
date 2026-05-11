package com.stayops.payment.infrastructure.scheduler

import com.stayops.reservation.application.service.ReservationPaymentOutboxApplication
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PaymentOutboxScheduler(
    private val reservationPaymentOutboxApplication: ReservationPaymentOutboxApplication,
    @Value("\${stayops.instance-id:\${HOSTNAME:local}}")
    private val instanceId: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${stayops.payment-outbox.fixed-delay-ms:30000}",
        initialDelayString = "\${stayops.payment-outbox.initial-delay-ms:60000}"
    )
    fun processPaymentOutbox() {
        try {
            reservationPaymentOutboxApplication.processPendingMessages("payment-outbox-$instanceId")
        } catch (e: Exception) {
            log.error("PaymentOutbox scheduler 처리 실패", e)
        }
    }
}
