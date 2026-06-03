package com.stayops.payment.infrastructure.scheduler

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled

class PaymentOutboxSchedulerTest {

    @Test
    fun `payment outbox scheduler uses short startup and polling intervals by default`() {
        val scheduled = PaymentOutboxScheduler::class.java
            .getDeclaredMethod("processPaymentOutbox")
            .getAnnotation(Scheduled::class.java)

        assertThat(scheduled.fixedDelayString).isEqualTo("\${stayops.payment-outbox.fixed-delay-ms:5000}")
        assertThat(scheduled.initialDelayString).isEqualTo("\${stayops.payment-outbox.initial-delay-ms:5000}")
    }
}
