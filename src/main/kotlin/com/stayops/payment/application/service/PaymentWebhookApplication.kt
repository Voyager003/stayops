package com.stayops.payment.application.service

import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.payment.domain.service.PaymentInquiryResult
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.exception.BusinessException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock

@Service
class PaymentWebhookApplication(
    private val paymentRepository: PaymentRepository,
    private val paymentOutboxRepository: PaymentOutboxRepository,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) {

    @Transactional
    fun handleTossPaymentStatusChanged(command: PaymentStatusChangedWebhookCommand) {
        if (command.eventType != PAYMENT_STATUS_CHANGED) {
            return
        }

        val payment = paymentRepository.findByOrderId(command.orderId) ?: return
        val inquiry = paymentGateway.inquire(command.paymentKey)
        validateInquiry(payment, command.paymentKey, inquiry)

        when (inquiry.status.uppercase()) {
            "IN_PROGRESS", "DONE" -> requestConfirm(payment, command.paymentKey)
            "EXPIRED", "ABORTED" -> failPaymentIfWaiting(payment, inquiry.status)
        }
    }

    private fun validateInquiry(
        payment: Payment,
        paymentKey: String,
        inquiry: PaymentInquiryResult
    ) {
        if (
            inquiry.paymentKey != paymentKey ||
            inquiry.orderId != payment.orderId ||
            inquiry.totalAmount.compareTo(payment.amount.amount) != 0
        ) {
            throw BusinessException(
                code = "PAYMENT_WEBHOOK_MISMATCH",
                message = "외부 결제 상태 조회 결과가 내부 결제 정보와 일치하지 않습니다"
            )
        }
    }

    private fun requestConfirm(payment: Payment, paymentKey: String) {
        if (payment.status == PaymentStatus.APPROVED || payment.status == PaymentStatus.FAILED) {
            return
        }
        if (
            payment.status == PaymentStatus.CANCEL_REQUESTED ||
            payment.status == PaymentStatus.CANCELLED ||
            payment.status == PaymentStatus.CANCEL_FAILED
        ) {
            return
        }

        val requestedPayment = payment.requestConfirm(paymentKey)
        val savedPayment = if (requestedPayment == payment) {
            payment
        } else {
            paymentRepository.save(requestedPayment)
        }

        val existingOutbox = paymentOutboxRepository.findByPaymentIdAndType(
            savedPayment.id,
            PaymentOutboxType.CONFIRM_PAYMENT
        )
        if (existingOutbox == null) {
            paymentOutboxRepository.save(
                PaymentOutboxMessage.createConfirm(
                    id = idGenerator.generate(),
                    paymentId = savedPayment.id,
                    reservationId = savedPayment.reservationId,
                    memberId = savedPayment.memberId,
                    paymentKey = paymentKey,
                    orderId = savedPayment.orderId,
                    amount = savedPayment.amount,
                    now = clock.instant()
                )
            )
        }
    }

    private fun failPaymentIfWaiting(payment: Payment, externalStatus: String) {
        if (payment.status == PaymentStatus.PENDING || payment.status == PaymentStatus.CONFIRM_REQUESTED) {
            paymentRepository.save(payment.fail("외부 결제 상태가 실패로 종료되었습니다: $externalStatus"))
        }
    }

    companion object {
        private const val PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED"
    }
}

data class PaymentStatusChangedWebhookCommand(
    val eventType: String,
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: BigDecimal
)
