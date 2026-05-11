package com.stayops.payment.infrastructure.reservation

import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.reservation.application.port.ReservationPaymentStatus
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

class PaymentReservationAdapterTest : BehaviorSpec({

    val paymentRepository = mockk<PaymentRepository>()
    val paymentOutboxRepository = mockk<PaymentOutboxRepository>()
    val fixedInstant = Instant.parse("2026-04-15T05:00:00Z")
    val clock = Clock.fixed(fixedInstant, ZoneId.of("Asia/Seoul"))
    val idGenerator = object : IdGenerator {
        override fun generate(): String = "outbox-id"
    }
    val adapter = PaymentReservationAdapter(
        paymentRepository = paymentRepository,
        paymentOutboxRepository = paymentOutboxRepository,
        idGenerator = idGenerator,
        clock = clock
    )

    fun pendingPayment() = Payment.create(
        id = "pay-1",
        reservationId = "rsv-1",
        memberId = "member-1",
        amount = Money.won(200_000)
    )

    beforeTest {
        clearAllMocks()
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { paymentOutboxRepository.save(any()) } answers { firstArg() }
    }

    given("예약 결제 Port adapter") {

        `when`("예약에 대한 PENDING 결제를 생성하면") {
            then("Payment를 생성하고 reservation 전용 snapshot으로 반환한다") {
                every { paymentRepository.save(any()) } answers { firstArg() }

                val result = adapter.createPendingPayment(
                    reservationId = "rsv-1",
                    memberId = "member-1",
                    amount = Money.won(200_000)
                )

                result.reservationId shouldBe "rsv-1"
                result.status shouldBe ReservationPaymentStatus.PENDING
                verify {
                    paymentRepository.save(match {
                        it.reservationId == "rsv-1" &&
                            it.memberId == "member-1" &&
                            it.amount == Money.won(200_000) &&
                            it.status == PaymentStatus.PENDING
                    })
                }
            }
        }

        `when`("결제 승인 요청을 접수하면") {
            then("Payment를 CONFIRM_REQUESTED로 변경하고 confirm Outbox를 생성한다") {
                val payment = pendingPayment()
                every { paymentRepository.findByReservationId("rsv-1") } returns payment
                every { paymentOutboxRepository.findByPaymentIdAndType("pay-1", PaymentOutboxType.CONFIRM_PAYMENT) } returns null

                val result = adapter.requestConfirm(
                    reservationId = "rsv-1",
                    memberId = "member-1",
                    paymentKey = "toss_pk_123",
                    orderId = payment.orderId,
                    amount = BigDecimal(200_000)
                )

                result.status shouldBe ReservationPaymentStatus.CONFIRM_REQUESTED
                result.paymentKey shouldBe "toss_pk_123"
                verify { paymentRepository.save(match { it.status == PaymentStatus.CONFIRM_REQUESTED }) }
                verify {
                    paymentOutboxRepository.save(match {
                        it.id == "outbox-id" &&
                            it.type == PaymentOutboxType.CONFIRM_PAYMENT &&
                            it.status == PaymentOutboxStatus.PENDING &&
                            it.idempotencyKey == "payment-confirm:pay-1:${payment.orderId}"
                    })
                }
            }
        }

        `when`("승인 요청 금액이 내부 결제 금액과 다르면") {
            then("Payment와 Outbox를 변경하지 않는다") {
                val payment = pendingPayment()
                every { paymentRepository.findByReservationId("rsv-1") } returns payment

                val ex = shouldThrow<BusinessException> {
                    adapter.requestConfirm(
                        reservationId = "rsv-1",
                        memberId = "member-1",
                        paymentKey = "toss_pk_123",
                        orderId = payment.orderId,
                        amount = BigDecimal(1_000)
                    )
                }

                ex.code shouldBe "AMOUNT_MISMATCH"
                verify(exactly = 0) { paymentRepository.save(any()) }
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
            }
        }

        `when`("확정 예약의 결제 취소 요청을 접수하면") {
            then("Payment를 CANCEL_REQUESTED로 변경하고 cancel Outbox를 생성한다") {
                val payment = pendingPayment().approve(
                    paymentKey = "toss_pk_456",
                    method = "카드",
                    approvedAt = fixedInstant
                )
                every { paymentRepository.findByReservationId("rsv-1") } returns payment
                every { paymentOutboxRepository.findByPaymentIdAndType("pay-1", PaymentOutboxType.CANCEL_PAYMENT) } returns null

                val result = adapter.requestCancelByCustomerRequest(
                    reservationId = "rsv-1",
                    memberId = "member-1"
                )

                result.status shouldBe ReservationPaymentStatus.CANCEL_REQUESTED
                verify { paymentRepository.save(match { it.status == PaymentStatus.CANCEL_REQUESTED }) }
                verify {
                    paymentOutboxRepository.save(match {
                        it.type == PaymentOutboxType.CANCEL_PAYMENT &&
                            it.status == PaymentOutboxStatus.PENDING &&
                            it.cancelReason == "고객 요청에 의한 취소"
                    })
                }
            }
        }

        `when`("PENDING 예약을 취소하면") {
            then("PG 취소 Outbox 없이 결제를 실패 처리한다") {
                val payment = pendingPayment()
                every { paymentRepository.findByReservationId("rsv-1") } returns payment

                val result = adapter.cancelPendingByCustomerRequest("rsv-1")

                result.status shouldBe ReservationPaymentStatus.FAILED
                result.failReason shouldBe "고객 요청에 의한 취소"
                verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
            }
        }
    }
})
