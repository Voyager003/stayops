package com.stayops.payment.application.service

import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.model.ProcessedPaymentWebhookEvent
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.repository.ProcessedPaymentWebhookEventRepository
import com.stayops.payment.application.required.PaymentGateway
import com.stayops.payment.application.required.PaymentInquiryResult
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock

@Service
class PaymentWebhookApplication(
    private val paymentRepository: PaymentRepository,
    private val paymentOutboxRepository: PaymentOutboxRepository,
    private val processedWebhookEventRepository: ProcessedPaymentWebhookEventRepository,
    private val paymentGateway: PaymentGateway,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handleTossPaymentStatusChanged(command: PaymentStatusChangedWebhookCommand) {
        if (command.eventType != PAYMENT_STATUS_CHANGED) {
            log.debug(
                "지원하지 않는 결제 웹훅 이벤트 건너뜀: eventType={}, transmissionId={}, orderId={}",
                command.eventType,
                command.transmissionId,
                command.orderId
            )
            return
        }
        if (
            command.transmissionId != null &&
            processedWebhookEventRepository.existsByTransmissionId(command.transmissionId)
        ) {
            log.info(
                "결제 웹훅 중복 수신 건너뜀: transmissionId={}, eventType={}, orderId={}",
                command.transmissionId,
                command.eventType,
                command.orderId
            )
            return
        }

        val payment = paymentRepository.findByOrderId(command.orderId)
        if (payment == null) {
            log.warn(
                "결제 웹훅 내부 결제 미발견: transmissionId={}, eventType={}, orderId={}, paymentKeySuffix={}",
                command.transmissionId,
                command.eventType,
                command.orderId,
                paymentKeySuffix(command.paymentKey)
            )
            return
        }
        val inquiry = paymentGateway.inquire(command.paymentKey)
        validateInquiry(payment, command.paymentKey, inquiry)

        when (inquiry.status.uppercase()) {
            "IN_PROGRESS", "DONE" -> requestConfirm(payment, command.paymentKey)
            "EXPIRED", "ABORTED" -> failPaymentIfWaiting(payment, inquiry.status)
        }

        recordProcessedEvent(command)
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
            log.warn(
                "결제 웹훅 조회 결과 불일치: paymentId={}, orderId={}, inquiryOrderId={}, paymentKeySuffix={}, " +
                    "inquiryPaymentKeySuffix={}, expectedAmount={}, inquiryAmount={}",
                payment.id,
                payment.orderId,
                inquiry.orderId,
                paymentKeySuffix(paymentKey),
                paymentKeySuffix(inquiry.paymentKey),
                payment.amount.amount,
                inquiry.totalAmount
            )
            throw BusinessException(
                code = "PAYMENT_WEBHOOK_MISMATCH",
                message = "외부 결제 상태 조회 결과가 내부 결제 정보와 일치하지 않습니다"
            )
        }
    }

    private fun requestConfirm(payment: Payment, paymentKey: String) {
        if (payment.status == PaymentStatus.APPROVED || payment.status == PaymentStatus.FAILED) {
            log.info(
                "결제 웹훅 승인 요청 건너뜀: paymentId={}, orderId={}, paymentStatus={}",
                payment.id,
                payment.orderId,
                payment.status
            )
            return
        }
        if (
            payment.status == PaymentStatus.CANCEL_REQUESTED ||
            payment.status == PaymentStatus.CANCELLED ||
            payment.status == PaymentStatus.CANCEL_FAILED
        ) {
            log.info(
                "결제 웹훅 승인 요청 건너뜀: paymentId={}, orderId={}, paymentStatus={}",
                payment.id,
                payment.orderId,
                payment.status
            )
            return
        }

        val requestedPayment = payment.requestConfirm(paymentKey)
        val savedPayment = if (requestedPayment === payment) {
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
                    reservationId = savedPayment.requireReservationId(),
                    memberId = savedPayment.memberId,
                    paymentKey = paymentKey,
                    orderId = savedPayment.orderId,
                    amount = savedPayment.amount,
                    now = clock.instant()
                )
            )
            log.info(
                "결제 웹훅 승인 Outbox 생성: paymentId={}, reservationId={}, orderId={}, paymentKeySuffix={}",
                savedPayment.id,
                savedPayment.reservationId,
                savedPayment.orderId,
                paymentKeySuffix(paymentKey)
            )
        } else {
            log.info(
                "결제 웹훅 승인 Outbox 중복 생성 건너뜀: paymentId={}, outboxId={}, orderId={}",
                savedPayment.id,
                existingOutbox.id,
                savedPayment.orderId
            )
        }
    }

    private fun failPaymentIfWaiting(payment: Payment, externalStatus: String) {
        if (payment.status == PaymentStatus.PENDING || payment.status == PaymentStatus.CONFIRM_REQUESTED) {
            paymentRepository.save(payment.fail("외부 결제 상태가 실패로 종료되었습니다: $externalStatus"))
            log.info(
                "결제 웹훅 실패 상태 반영: paymentId={}, orderId={}, externalStatus={}",
                payment.id,
                payment.orderId,
                externalStatus
            )
        } else {
            log.info(
                "결제 웹훅 실패 상태 반영 건너뜀: paymentId={}, orderId={}, paymentStatus={}, externalStatus={}",
                payment.id,
                payment.orderId,
                payment.status,
                externalStatus
            )
        }
    }

    private fun recordProcessedEvent(command: PaymentStatusChangedWebhookCommand) {
        val transmissionId = command.transmissionId ?: return
        processedWebhookEventRepository.saveIfAbsent(
            ProcessedPaymentWebhookEvent(
                id = idGenerator.generate(),
                transmissionId = transmissionId,
                eventType = command.eventType,
                paymentKey = command.paymentKey,
                orderId = command.orderId,
                processedAt = clock.instant()
            )
        )
    }

    private fun paymentKeySuffix(paymentKey: String): String =
        paymentKey.takeLast(4)

    companion object {
        private const val PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED"
    }
}

data class PaymentStatusChangedWebhookCommand(
    val eventType: String,
    val transmissionId: String?,
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: BigDecimal
)
