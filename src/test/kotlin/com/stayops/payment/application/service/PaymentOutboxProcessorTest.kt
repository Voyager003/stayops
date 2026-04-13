package com.stayops.payment.application.service

import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentCancelReason
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.service.PaymentConfirmResult
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.payment.domain.service.PaymentGatewayException
import com.stayops.payment.domain.service.PaymentInquiryResult
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.ConflictException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PaymentOutboxProcessorTest : BehaviorSpec({

    val outboxRepository = mockk<PaymentOutboxRepository>()
    val paymentRepository = mockk<PaymentRepository>()
    val reservationRepository = mockk<ReservationRepository>()
    val inventoryReservationPort = mockk<InventoryReservationPort>()
    val paymentGateway = mockk<PaymentGateway>()
    val fixedInstant = Instant.parse("2026-04-13T10:00:00Z")
    val clock = Clock.fixed(fixedInstant, ZoneId.of("Asia/Seoul"))
    val idGenerator = object : IdGenerator {
        override fun generate(): String = "generated-outbox-id"
    }

    val processor = PaymentOutboxProcessor(
        outboxRepository = outboxRepository,
        paymentRepository = paymentRepository,
        reservationRepository = reservationRepository,
        inventoryReservationPort = inventoryReservationPort,
        paymentGateway = paymentGateway,
        clock = clock,
        idGenerator = idGenerator
    )

    val checkIn = LocalDate.of(2026, 5, 1)
    val checkOut = LocalDate.of(2026, 5, 3)

    fun pendingReservation() = Reservation.create(
        id = "rsv-1",
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = GuestInfo("김고객", "010-1111-2222", null),
        dateRange = DateRange.of(checkIn, checkOut),
        numberOfGuests = 2,
        channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
        memberId = "member-1"
    )

    fun pendingPayment() = Payment.create(
        id = "pay-1",
        reservationId = "rsv-1",
        memberId = "member-1",
        amount = Money.won(200_000)
    )

    fun confirmRequestedPayment() = pendingPayment().requestConfirm("toss_pk_123")

    fun approvedPayment() = pendingPayment().approve(
        paymentKey = "toss_pk_123",
        method = "카드",
        approvedAt = fixedInstant
    )

    fun cancelRequestedPayment() = approvedPayment().requestCancel()

    fun confirmMessage() = PaymentOutboxMessage.createConfirm(
        id = "outbox-1",
        paymentId = "pay-1",
        reservationId = "rsv-1",
        memberId = "member-1",
        paymentKey = "toss_pk_123",
        orderId = pendingPayment().orderId,
        amount = Money.won(200_000),
        now = fixedInstant
    )

    fun cancelMessage() = PaymentOutboxMessage.createCancel(
        id = "outbox-2",
        paymentId = "pay-1",
        reservationId = "rsv-1",
        memberId = "member-1",
        paymentKey = "toss_pk_123",
        orderId = pendingPayment().orderId,
        amount = Money.won(200_000),
        cancelReason = "고객 요청에 의한 취소",
        now = fixedInstant
    )

    beforeTest {
        clearAllMocks()
        every { outboxRepository.save(any()) } answers { firstArg() }
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { reservationRepository.save(any()) } answers { firstArg() }
        every { outboxRepository.findByPaymentIdAndType(any(), any()) } returns null
        every { inventoryReservationPort.reserve(any(), any(), any()) } returns Unit
        every { inventoryReservationPort.release(any(), any(), any()) } returns Unit
    }

    given("결제 승인 Outbox 처리 시") {
        `when`("PG 승인에 성공하면") {
            then("Payment 승인과 Reservation 확정 후 Outbox를 완료한다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation()
                every {
                    paymentGateway.confirm(
                        paymentKey = "toss_pk_123",
                        orderId = message.orderId,
                        amount = BigDecimal(200_000),
                        idempotencyKey = message.idempotencyKey
                    )
                } returns PaymentConfirmResult(
                    paymentKey = "toss_pk_123",
                    orderId = message.orderId,
                    method = "카드",
                    approvedAt = fixedInstant,
                    totalAmount = BigDecimal(200_000),
                    receiptUrl = null,
                    cardNumber = null,
                    cardCompany = null
                )

                processor.processPendingMessages(workerId = "worker-1")

                verify(exactly = 2) { inventoryReservationPort.reserve("prop-1", "rt-1", any()) }
                verify { paymentRepository.save(match { it.status == PaymentStatus.APPROVED }) }
                verify { reservationRepository.save(match { it.status == ReservationStatus.CONFIRMED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("PG가 이미 처리됐다고 응답하고 조회 결과 DONE이면") {
            then("외부 상태 조회 결과로 내부 결제와 예약 상태를 복구한다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation()
                every { paymentGateway.confirm(any(), any(), any(), any()) } throws
                    PaymentGatewayException.AlreadyProcessed("toss_pk_123")
                every { paymentGateway.inquire("toss_pk_123") } returns PaymentInquiryResult(
                    paymentKey = "toss_pk_123",
                    orderId = message.orderId,
                    status = "DONE",
                    totalAmount = BigDecimal(200_000)
                )

                processor.processPendingMessages(workerId = "worker-1")

                verify(exactly = 2) { inventoryReservationPort.reserve("prop-1", "rt-1", any()) }
                verify { paymentRepository.save(match { it.status == PaymentStatus.APPROVED }) }
                verify { reservationRepository.save(match { it.status == ReservationStatus.CONFIRMED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("PG 승인 성공 후 재고가 부족하면") {
            then("Payment를 CANCEL_REQUESTED로 바꾸고 재고 부족 보상 취소 Outbox를 생성한다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation()
                every {
                    paymentGateway.confirm(
                        paymentKey = "toss_pk_123",
                        orderId = message.orderId,
                        amount = BigDecimal(200_000),
                        idempotencyKey = message.idempotencyKey
                    )
                } returns PaymentConfirmResult(
                    paymentKey = "toss_pk_123",
                    orderId = message.orderId,
                    method = "카드",
                    approvedAt = fixedInstant,
                    totalAmount = BigDecimal(200_000),
                    receiptUrl = null,
                    cardNumber = null,
                    cardCompany = null
                )
                every { inventoryReservationPort.reserve("prop-1", "rt-1", checkIn) } throws
                    IllegalArgumentException("가용 객실이 없습니다: available=0")

                processor.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.CANCEL_REQUESTED }) }
                verify { reservationRepository.save(match { it.status == ReservationStatus.CANCELLED }) }
                verify {
                    outboxRepository.save(match {
                        it.id == "generated-outbox-id" &&
                            it.type == PaymentOutboxType.CANCEL_PAYMENT &&
                            it.status == PaymentOutboxStatus.PENDING &&
                            it.cancelReason == PaymentCancelReason.INVENTORY_UNAVAILABLE.message
                    })
                }
                verify { outboxRepository.save(match { it.id == "outbox-1" && it.status == PaymentOutboxStatus.COMPLETED }) }
                verify(exactly = 0) { reservationRepository.save(match { it.status == ReservationStatus.CONFIRMED }) }
            }
        }

        `when`("PG 승인 성공 후 일부 재고 차감 뒤 재고 변경 충돌이 발생하면") {
            then("이미 차감한 재고를 복원하고 Outbox를 재시도 대기로 되돌린다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation()
                every {
                    paymentGateway.confirm(
                        paymentKey = "toss_pk_123",
                        orderId = message.orderId,
                        amount = BigDecimal(200_000),
                        idempotencyKey = message.idempotencyKey
                    )
                } returns PaymentConfirmResult(
                    paymentKey = "toss_pk_123",
                    orderId = message.orderId,
                    method = "카드",
                    approvedAt = fixedInstant,
                    totalAmount = BigDecimal(200_000),
                    receiptUrl = null,
                    cardNumber = null,
                    cardCompany = null
                )
                every { inventoryReservationPort.reserve("prop-1", "rt-1", checkIn) } returns Unit
                every { inventoryReservationPort.reserve("prop-1", "rt-1", checkIn.plusDays(1)) } throws
                    ConflictException("INVENTORY_CONFLICT", "재고 변경 충돌이 발생했습니다")

                processor.processPendingMessages(workerId = "worker-1")

                verify(exactly = 1) { inventoryReservationPort.release("prop-1", "rt-1", checkIn) }
                verify(exactly = 0) { paymentRepository.save(match { it.status == PaymentStatus.CANCEL_REQUESTED }) }
                verify(exactly = 0) { reservationRepository.save(match { it.status == ReservationStatus.CONFIRMED }) }
                verify {
                    outboxRepository.save(match {
                        it.id == "outbox-1" &&
                            it.status == PaymentOutboxStatus.PENDING &&
                            it.retryCount == 1
                    })
                }
            }
        }

        `when`("PG가 재시도 가능한 오류를 반환하면") {
            then("Payment와 Reservation은 유지하고 Outbox만 재시도 대기로 되돌린다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation()
                every { paymentGateway.confirm(any(), any(), any(), any()) } throws
                    PaymentGatewayException.ProviderError("PROVIDER_ERROR", "PG사 장애")
                every { paymentGateway.inquire("toss_pk_123") } throws
                    PaymentGatewayException.ProviderError("PROVIDER_ERROR", "PG사 장애")

                processor.processPendingMessages(workerId = "worker-1")

                verify(exactly = 0) { paymentRepository.save(match { it.status == PaymentStatus.APPROVED }) }
                verify(exactly = 0) { reservationRepository.save(match { it.status == ReservationStatus.CONFIRMED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.PENDING && it.retryCount == 1 }) }
            }
        }

        `when`("PG가 재시도 가능한 오류를 반환하고 조회 결과가 DONE이 아니면") {
            then("Payment와 Reservation은 유지하고 Outbox만 재시도 대기로 되돌린다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation()
                every { paymentGateway.confirm(any(), any(), any(), any()) } throws
                    PaymentGatewayException.ProviderError("PROVIDER_ERROR", "PG사 장애")
                every { paymentGateway.inquire("toss_pk_123") } returns PaymentInquiryResult(
                    paymentKey = "toss_pk_123",
                    orderId = message.orderId,
                    status = "IN_PROGRESS",
                    totalAmount = BigDecimal(200_000)
                )

                processor.processPendingMessages(workerId = "worker-1")

                verify(exactly = 0) { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify(exactly = 0) { reservationRepository.save(match { it.status == ReservationStatus.CONFIRMED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.PENDING && it.retryCount == 1 }) }
            }
        }

        `when`("예약이 이미 취소된 상태이면") {
            then("PG를 호출하지 않고 Outbox를 건너뛴다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment().fail("고객 요청에 의한 취소")
                every { reservationRepository.findById("rsv-1") } returns pendingReservation().cancelPending()

                processor.processPendingMessages(workerId = "worker-1")

                verify(exactly = 0) { paymentGateway.confirm(any(), any(), any(), any()) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.SKIPPED }) }
            }
        }
    }

    given("결제 취소 Outbox 처리 시") {
        `when`("PG 취소에 성공하면") {
            then("Payment 취소 후 Outbox를 완료한다") {
                val message = cancelMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns cancelRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation().confirm().cancel()
                every {
                    paymentGateway.cancel(
                        paymentKey = "toss_pk_123",
                        cancelReason = "고객 요청에 의한 취소",
                        idempotencyKey = message.idempotencyKey
                    )
                } returns com.stayops.payment.domain.service.PaymentCancelResult("toss_pk_123")

                processor.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.CANCELLED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("PG가 이미 취소됐다고 응답하면") {
            then("Payment 취소 후 Outbox를 완료한다") {
                val message = cancelMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns cancelRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation().confirm().cancel()
                every { paymentGateway.cancel(any(), any(), any()) } throws
                    PaymentGatewayException.AlreadyProcessed("toss_pk_123")

                processor.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.CANCELLED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("PG 취소가 재시도 가능한 오류를 반환하면") {
            then("Payment는 CANCEL_REQUESTED로 유지하고 Outbox만 재시도 대기로 되돌린다") {
                val message = cancelMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns cancelRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation().confirm().cancel()
                every { paymentGateway.cancel(any(), any(), any()) } throws
                    PaymentGatewayException.ProviderError("PROVIDER_ERROR", "PG사 장애")

                processor.processPendingMessages(workerId = "worker-1")

                verify(exactly = 0) { paymentRepository.save(match { it.status == PaymentStatus.CANCELLED }) }
                verify(exactly = 0) { paymentRepository.save(match { it.status == PaymentStatus.CANCEL_FAILED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.PENDING && it.retryCount == 1 }) }
            }
        }

        `when`("Payment가 이미 CANCELLED이면") {
            then("PG를 호출하지 않고 Outbox를 완료한다") {
                val message = cancelMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns approvedPayment().cancel()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation().confirm().cancel()

                processor.processPendingMessages(workerId = "worker-1")

                verify(exactly = 0) { paymentGateway.cancel(any(), any(), any()) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }
    }
})
