package com.stayops.payment.application.service

import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.repository.ProcessedPaymentWebhookEventRepository
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.payment.domain.service.PaymentInquiryResult
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PaymentWebhookApplicationTest : BehaviorSpec({

    val paymentRepository = mockk<PaymentRepository>()
    val paymentOutboxRepository = mockk<PaymentOutboxRepository>()
    val processedWebhookEventRepository = mockk<ProcessedPaymentWebhookEventRepository>()
    val paymentGateway = mockk<PaymentGateway>()
    val fixedInstant = Instant.parse("2026-04-14T10:00:00Z")
    val clock = Clock.fixed(fixedInstant, ZoneId.of("Asia/Seoul"))
    val idGenerator = object : IdGenerator {
        override fun generate(): String = "generated-outbox-id"
    }

    val sut = PaymentWebhookApplication(
        paymentRepository = paymentRepository,
        paymentOutboxRepository = paymentOutboxRepository,
        processedWebhookEventRepository = processedWebhookEventRepository,
        paymentGateway = paymentGateway,
        clock = clock,
        idGenerator = idGenerator
    )

    fun pendingPayment() = Payment.create(
        id = "pay-1",
        reservationId = "rsv-1",
        memberId = "member-1",
        amount = Money.won(200_000)
    )

    fun command(
        payment: Payment = pendingPayment(),
        externalStatus: String = "IN_PROGRESS",
        paymentKey: String = "toss_pk_123",
        transmissionId: String? = "tx-webhook-1"
    ) = PaymentStatusChangedWebhookCommand(
        eventType = "PAYMENT_STATUS_CHANGED",
        transmissionId = transmissionId,
        paymentKey = paymentKey,
        orderId = payment.orderId,
        status = externalStatus,
        totalAmount = BigDecimal(200_000)
    )

    beforeTest {
        clearAllMocks()
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { paymentOutboxRepository.save(any()) } answers { firstArg() }
        every { paymentOutboxRepository.findByPaymentIdAndType(any(), any()) } returns null
        every { processedWebhookEventRepository.existsByTransmissionId(any()) } returns false
        every { processedWebhookEventRepository.saveIfAbsent(any()) } returns true
    }

    given("Toss PAYMENT_STATUS_CHANGED 웹훅 수신 시") {
        `when`("PG 조회 결과가 IN_PROGRESS이면") {
            then("결제 승인 요청 상태와 CONFIRM_PAYMENT Outbox를 생성한다") {
                val payment = pendingPayment()
                every { paymentRepository.findByOrderId(payment.orderId) } returns payment
                every { paymentGateway.inquire("toss_pk_123") } returns PaymentInquiryResult(
                    paymentKey = "toss_pk_123",
                    orderId = payment.orderId,
                    status = "IN_PROGRESS",
                    totalAmount = BigDecimal(200_000)
                )

                sut.handleTossPaymentStatusChanged(command(payment))

                verify { paymentRepository.save(match { it.status == PaymentStatus.CONFIRM_REQUESTED }) }
                verify {
                    paymentOutboxRepository.save(match {
                        it.id == "generated-outbox-id" &&
                            it.type == PaymentOutboxType.CONFIRM_PAYMENT &&
                            it.status == PaymentOutboxStatus.PENDING &&
                            it.paymentId == "pay-1" &&
                            it.paymentKey == "toss_pk_123"
                    })
                }
                verify {
                    processedWebhookEventRepository.saveIfAbsent(match {
                        it.transmissionId == "tx-webhook-1" &&
                            it.eventType == "PAYMENT_STATUS_CHANGED" &&
                            it.paymentKey == "toss_pk_123" &&
                            it.orderId == payment.orderId
                    })
                }
            }
        }

        `when`("이미 처리한 transmissionId이면") {
            then("PG 조회 없이 이벤트를 건너뛴다") {
                val payment = pendingPayment()
                every { processedWebhookEventRepository.existsByTransmissionId("tx-processed") } returns true

                sut.handleTossPaymentStatusChanged(
                    command(payment, transmissionId = "tx-processed")
                )

                verify(exactly = 0) { paymentRepository.findByOrderId(any()) }
                verify(exactly = 0) { paymentGateway.inquire(any()) }
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
            }
        }

        `when`("이미 승인 Outbox가 있으면") {
            then("Outbox를 중복 생성하지 않는다") {
                val payment = pendingPayment().requestConfirm("toss_pk_123")
                val existingOutbox = PaymentOutboxMessage.createConfirm(
                    id = "outbox-existing",
                    paymentId = payment.id,
                    reservationId = payment.reservationId,
                    memberId = payment.memberId,
                    paymentKey = "toss_pk_123",
                    orderId = payment.orderId,
                    amount = payment.amount,
                    now = fixedInstant
                )
                every { paymentRepository.findByOrderId(payment.orderId) } returns payment
                every { paymentGateway.inquire("toss_pk_123") } returns PaymentInquiryResult(
                    paymentKey = "toss_pk_123",
                    orderId = payment.orderId,
                    status = "DONE",
                    totalAmount = BigDecimal(200_000)
                )
                every {
                    paymentOutboxRepository.findByPaymentIdAndType(payment.id, PaymentOutboxType.CONFIRM_PAYMENT)
                } returns existingOutbox

                sut.handleTossPaymentStatusChanged(command(payment, externalStatus = "DONE"))

                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
            }
        }

        `when`("payload와 무관하게 PG 조회 결과 금액이 내부 결제와 다르면") {
            then("외부 이벤트를 신뢰하지 않고 BusinessException을 던진다") {
                val payment = pendingPayment()
                every { paymentRepository.findByOrderId(payment.orderId) } returns payment
                every { paymentGateway.inquire("toss_pk_123") } returns PaymentInquiryResult(
                    paymentKey = "toss_pk_123",
                    orderId = payment.orderId,
                    status = "IN_PROGRESS",
                    totalAmount = BigDecimal(300_000)
                )

                val exception = shouldThrow<BusinessException> {
                    sut.handleTossPaymentStatusChanged(command(payment))
                }

                exception.code shouldBe "PAYMENT_WEBHOOK_MISMATCH"
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
            }
        }

        `when`("PG 조회 결과가 EXPIRED이면") {
            then("내부 결제를 실패 상태로 전이하고 Outbox를 만들지 않는다") {
                val payment = pendingPayment()
                every { paymentRepository.findByOrderId(payment.orderId) } returns payment
                every { paymentGateway.inquire("toss_pk_123") } returns PaymentInquiryResult(
                    paymentKey = "toss_pk_123",
                    orderId = payment.orderId,
                    status = "EXPIRED",
                    totalAmount = BigDecimal(200_000)
                )

                sut.handleTossPaymentStatusChanged(command(payment, externalStatus = "EXPIRED"))

                verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
            }
        }

        `when`("내부 결제를 찾을 수 없으면") {
            then("재시도 폭주를 막기 위해 이벤트를 무시한다") {
                val payment = pendingPayment()
                every { paymentRepository.findByOrderId(payment.orderId) } returns null

                sut.handleTossPaymentStatusChanged(command(payment))

                verify(exactly = 0) { paymentGateway.inquire(any()) }
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
            }
        }
    }
})
