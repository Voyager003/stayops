package com.stayops.reservation.application.service

import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.provided.InventoryHoldService
import com.stayops.inventory.application.provided.InventoryReservationService
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentCancelReason
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.application.required.PaymentConfirmResult
import com.stayops.payment.application.required.PaymentGateway
import com.stayops.payment.application.required.PaymentGatewayException
import com.stayops.payment.application.required.PaymentInquiryResult
import com.stayops.reservation.domain.event.ReservationCreated
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.ConflictException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReservationPaymentOutboxApplicationTest : BehaviorSpec({

    val outboxRepository = mockk<PaymentOutboxRepository>()
    val paymentRepository = mockk<PaymentRepository>()
    val reservationRepository = mockk<ReservationRepository>()
    val reservationIntentRepository = mockk<ReservationIntentRepository>()
    val guestRepository = mockk<GuestRepository>()
    val inventoryReservationService = mockk<InventoryReservationService>()
    val inventoryHoldService = mockk<InventoryHoldService>()
    val paymentGateway = mockk<PaymentGateway>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val fixedInstant = Instant.parse("2026-04-13T10:00:00Z")
    val clock = Clock.fixed(fixedInstant, ZoneId.of("Asia/Seoul"))
    val idGenerator = object : IdGenerator {
        override fun generate(): String = "generated-outbox-id"
    }

    val application = ReservationPaymentOutboxApplication(
        outboxRepository = outboxRepository,
        paymentRepository = paymentRepository,
        reservationRepository = reservationRepository,
        reservationIntentRepository = reservationIntentRepository,
        guestRepository = guestRepository,
        inventoryReservationService = inventoryReservationService,
        inventoryHoldService = inventoryHoldService,
        paymentGateway = paymentGateway,
        eventPublisher = eventPublisher,
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

    fun reservationIntent() = ReservationIntent.create(
        id = "intent-1",
        memberId = "member-1",
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        guestInfo = GuestInfo("김고객", "010-1111-2222", "kim@test.com"),
        dateRange = DateRange.of(checkIn, checkOut),
        numberOfGuests = 2,
        channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
        paymentId = "pay-intent-1",
        holdId = "hold-1",
        expiresAt = fixedInstant.plusSeconds(900),
        now = fixedInstant
    ).requestPaymentConfirmation(fixedInstant)

    fun pendingPayment() = Payment.create(
        id = "pay-1",
        reservationId = "rsv-1",
        memberId = "member-1",
        amount = Money.won(200_000)
    )

    fun confirmRequestedPayment() = pendingPayment().requestConfirm("toss_pk_123")

    fun pendingIntentPayment() = Payment.createForReservationIntent(
        id = "pay-intent-1",
        reservationIntentId = "intent-1",
        memberId = "member-1",
        amount = Money.won(200_000)
    )

    fun confirmRequestedIntentPayment() = pendingIntentPayment().requestConfirm("toss_pk_123")

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

    fun confirmIntentMessage() = PaymentOutboxMessage.createConfirmForReservationIntent(
        id = "outbox-intent-1",
        paymentId = "pay-intent-1",
        reservationIntentId = "intent-1",
        memberId = "member-1",
        paymentKey = "toss_pk_123",
        orderId = pendingIntentPayment().orderId,
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
        every { reservationIntentRepository.save(any()) } answers { firstArg() }
        every { guestRepository.save(any()) } answers { firstArg() }
        every { outboxRepository.findByPaymentIdAndType(any(), any()) } returns null
        every { inventoryReservationService.reserve(any(), any(), any()) } returns Unit
        every { inventoryReservationService.release(any(), any(), any()) } returns Unit
        every { inventoryHoldService.consume(any()) } returns Unit
        every { inventoryHoldService.release(any()) } returns Unit
    }

    given("결제 승인 Outbox 처리 시") {
        `when`("PG 승인에 성공하면") {
            then("Payment 승인과 재고 차감 후 Reservation은 PENDING으로 유지하고 Outbox를 완료한다") {
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
                    totalAmount = BigDecimal(200_000)
                )

                application.processPendingMessages(workerId = "worker-1")

                verify(exactly = 2) { inventoryReservationService.reserve("prop-1", "rt-1", any()) }
                verify { paymentRepository.save(match { it.status == PaymentStatus.APPROVED }) }
                verify(exactly = 0) { reservationRepository.save(match { it.status == ReservationStatus.CONFIRMED }) }
                verify {
                    eventPublisher.publishEvent(match<ReservationCreated> {
                        it.reservationId == "rsv-1" &&
                            it.propertyId == "prop-1" &&
                            it.roomTypeId == "rt-1"
                    })
                }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("ReservationIntent 기반 결제의 PG 승인에 성공하면") {
            then("hold를 소비하고 최종 Reservation을 생성한 뒤 intent를 RESERVED로 변경한다") {
                val message = confirmIntentMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-intent-1") } returns confirmRequestedIntentPayment()
                every { reservationIntentRepository.findById("intent-1") } returns reservationIntent()
                every { guestRepository.findByPropertyIdAndPhone("prop-1", "010-1111-2222") } returns null
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
                    totalAmount = BigDecimal(200_000)
                )

                application.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.APPROVED }) }
                verify(exactly = 1) { inventoryHoldService.consume("intent-1") }
                verify(exactly = 0) { inventoryReservationService.reserve(any(), any(), any()) }
                verify { guestRepository.save(match { it.propertyId == "prop-1" && it.phone == "010-1111-2222" }) }
                verify {
                    reservationRepository.save(match {
                        it.status == ReservationStatus.CONFIRMED &&
                            it.propertyId == "prop-1" &&
                            it.roomTypeId == "rt-1" &&
                            it.memberId == "member-1"
                    })
                }
                verify {
                    reservationIntentRepository.save(match {
                        it.status == ReservationIntentStatus.RESERVED &&
                            it.reservationId != null
                    })
                }
                verify { eventPublisher.publishEvent(any<ReservationCreated>()) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("ReservationIntent 기반 결제에서 PG가 결제를 최종 거절하면") {
            then("Payment와 intent를 실패 처리하고 hold를 해제한다") {
                val message = confirmIntentMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-intent-1") } returns confirmRequestedIntentPayment()
                every { reservationIntentRepository.findById("intent-1") } returns reservationIntent()
                every { paymentGateway.confirm(any(), any(), any(), any()) } throws
                    PaymentGatewayException.PaymentDeclined("CARD_DECLINED", "카드 승인이 거절되었습니다")

                application.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify { reservationIntentRepository.save(match { it.status == ReservationIntentStatus.PAYMENT_FAILED }) }
                verify(exactly = 1) { inventoryHoldService.release("intent-1") }
                verify(exactly = 0) { inventoryHoldService.consume(any()) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("ReservationIntent 기반 결제에서 PG 승인 요청이 유효하지 않으면") {
            then("Payment와 intent를 실패 처리하고 hold를 해제한다") {
                val message = confirmIntentMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-intent-1") } returns confirmRequestedIntentPayment()
                every { reservationIntentRepository.findById("intent-1") } returns reservationIntent()
                every { paymentGateway.confirm(any(), any(), any(), any()) } throws
                    PaymentGatewayException.InvalidRequest("INVALID_REQUEST", "잘못된 결제 승인 요청입니다")

                application.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify { reservationIntentRepository.save(match { it.status == ReservationIntentStatus.PAYMENT_FAILED }) }
                verify(exactly = 1) { inventoryHoldService.release("intent-1") }
                verify(exactly = 0) { inventoryHoldService.consume(any()) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.SKIPPED }) }
            }
        }

        `when`("ReservationIntent 기반 결제에서 PG 승인 결과가 요청 값과 다르면") {
            then("Payment와 intent를 실패 처리하고 hold를 해제한다") {
                val message = confirmIntentMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-intent-1") } returns confirmRequestedIntentPayment()
                every { reservationIntentRepository.findById("intent-1") } returns reservationIntent()
                every {
                    paymentGateway.confirm(
                        paymentKey = "toss_pk_123",
                        orderId = message.orderId,
                        amount = BigDecimal(200_000),
                        idempotencyKey = message.idempotencyKey
                    )
                } returns PaymentConfirmResult(
                    paymentKey = "toss_pk_123",
                    orderId = "different-order-id",
                    method = "카드",
                    approvedAt = fixedInstant,
                    totalAmount = BigDecimal(200_000)
                )

                application.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify { reservationIntentRepository.save(match { it.status == ReservationIntentStatus.PAYMENT_FAILED }) }
                verify(exactly = 1) { inventoryHoldService.release("intent-1") }
                verify(exactly = 0) { inventoryHoldService.consume(any()) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.SKIPPED }) }
            }
        }

        `when`("ReservationIntent 기반 이미 처리된 결제 조회 결과가 DONE이 아니면") {
            then("Payment와 intent를 실패 처리하고 hold를 해제한다") {
                val message = confirmIntentMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-intent-1") } returns confirmRequestedIntentPayment()
                every { reservationIntentRepository.findById("intent-1") } returns reservationIntent()
                every { paymentGateway.confirm(any(), any(), any(), any()) } throws
                    PaymentGatewayException.AlreadyProcessed("toss_pk_123")
                every { paymentGateway.inquire("toss_pk_123") } returns PaymentInquiryResult(
                    paymentKey = "toss_pk_123",
                    orderId = message.orderId,
                    status = "ABORTED",
                    totalAmount = BigDecimal(200_000)
                )

                application.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify { reservationIntentRepository.save(match { it.status == ReservationIntentStatus.PAYMENT_FAILED }) }
                verify(exactly = 1) { inventoryHoldService.release("intent-1") }
                verify(exactly = 0) { inventoryHoldService.consume(any()) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("PG가 이미 처리됐다고 응답하고 조회 결과 DONE이면") {
            then("외부 상태 조회 결과로 결제를 승인하고 Reservation은 PENDING으로 유지한다") {
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

                application.processPendingMessages(workerId = "worker-1")

                verify(exactly = 2) { inventoryReservationService.reserve("prop-1", "rt-1", any()) }
                verify { paymentRepository.save(match { it.status == PaymentStatus.APPROVED }) }
                verify(exactly = 0) { reservationRepository.save(match { it.status == ReservationStatus.CONFIRMED }) }
                verify { eventPublisher.publishEvent(any<ReservationCreated>()) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("Payment는 이미 승인됐지만 Reservation이 아직 PENDING이면") {
            then("재고와 Reservation을 다시 변경하지 않고 Outbox만 완료한다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns approvedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation()

                application.processPendingMessages(workerId = "worker-1")

                verify(exactly = 0) { paymentGateway.confirm(any(), any(), any(), any()) }
                verify(exactly = 0) { inventoryReservationService.reserve(any(), any(), any()) }
                verify(exactly = 0) { reservationRepository.save(match { it.status == ReservationStatus.CONFIRMED }) }
                verify(exactly = 0) { eventPublisher.publishEvent(any<ReservationCreated>()) }
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
                    totalAmount = BigDecimal(200_000)
                )
                every { inventoryReservationService.reserve("prop-1", "rt-1", checkIn) } throws
                    IllegalArgumentException("가용 객실이 없습니다: available=0")

                application.processPendingMessages(workerId = "worker-1")

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
                    totalAmount = BigDecimal(200_000)
                )
                every { inventoryReservationService.reserve("prop-1", "rt-1", checkIn) } returns Unit
                every { inventoryReservationService.reserve("prop-1", "rt-1", checkIn.plusDays(1)) } throws
                    ConflictException("INVENTORY_CONFLICT", "재고 변경 충돌이 발생했습니다")

                application.processPendingMessages(workerId = "worker-1")

                verify(exactly = 1) { inventoryReservationService.release("prop-1", "rt-1", checkIn) }
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

                application.processPendingMessages(workerId = "worker-1")

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

                application.processPendingMessages(workerId = "worker-1")

                verify(exactly = 0) { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify(exactly = 0) { reservationRepository.save(match { it.status == ReservationStatus.CONFIRMED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.PENDING && it.retryCount == 1 }) }
            }
        }

        `when`("PG가 결제를 최종 거절하면") {
            then("Payment를 실패 처리하고 PENDING Reservation을 취소한다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation()
                every { paymentGateway.confirm(any(), any(), any(), any()) } throws
                    PaymentGatewayException.PaymentDeclined("CARD_DECLINED", "카드 승인이 거절되었습니다")

                application.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify { reservationRepository.save(match { it.status == ReservationStatus.CANCELLED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("PG 승인 요청이 유효하지 않으면") {
            then("Payment를 실패 처리하고 PENDING Reservation을 취소한다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation()
                every { paymentGateway.confirm(any(), any(), any(), any()) } throws
                    PaymentGatewayException.InvalidRequest("INVALID_REQUEST", "잘못된 결제 승인 요청입니다")

                application.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify { reservationRepository.save(match { it.status == ReservationStatus.CANCELLED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.SKIPPED }) }
            }
        }

        `when`("이미 처리된 결제 조회 결과가 DONE이 아니면") {
            then("Payment를 실패 처리하고 PENDING Reservation을 취소한다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment()
                every { reservationRepository.findById("rsv-1") } returns pendingReservation()
                every { paymentGateway.confirm(any(), any(), any(), any()) } throws
                    PaymentGatewayException.AlreadyProcessed("toss_pk_123")
                every { paymentGateway.inquire("toss_pk_123") } returns PaymentInquiryResult(
                    paymentKey = "toss_pk_123",
                    orderId = message.orderId,
                    status = "ABORTED",
                    totalAmount = BigDecimal(200_000)
                )

                application.processPendingMessages(workerId = "worker-1")

                verify { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                verify { reservationRepository.save(match { it.status == ReservationStatus.CANCELLED }) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }

        `when`("예약이 이미 취소된 상태이면") {
            then("PG를 호출하지 않고 Outbox를 건너뛴다") {
                val message = confirmMessage()
                every { outboxRepository.findReadyForProcessing(fixedInstant) } returns listOf(message)
                every { paymentRepository.findById("pay-1") } returns confirmRequestedPayment().fail("고객 요청에 의한 취소")
                every { reservationRepository.findById("rsv-1") } returns pendingReservation().cancelPending()

                application.processPendingMessages(workerId = "worker-1")

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
                } returns com.stayops.payment.application.required.PaymentCancelResult("toss_pk_123")

                application.processPendingMessages(workerId = "worker-1")

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

                application.processPendingMessages(workerId = "worker-1")

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

                application.processPendingMessages(workerId = "worker-1")

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

                application.processPendingMessages(workerId = "worker-1")

                verify(exactly = 0) { paymentGateway.cancel(any(), any(), any()) }
                verify { outboxRepository.save(match { it.status == PaymentOutboxStatus.COMPLETED }) }
            }
        }
    }
})
