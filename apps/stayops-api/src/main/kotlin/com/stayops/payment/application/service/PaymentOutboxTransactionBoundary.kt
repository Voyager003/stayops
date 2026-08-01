package com.stayops.payment.application.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentOutboxTransactionBoundary {

    @Transactional
    fun <T> required(block: () -> T): T = block()
}
