package com.stayops.reservation.application.service

import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.application.required.ReservationPaymentSnapshot
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationIntentRequest
import com.stayops.reservation.domain.model.ReservationIntentRequestAlreadyExistsException
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.reservation.domain.repository.ReservationIntentRequestRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.ConflictException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class CustomerReservationIntentIdempotencyApplicationTest : BehaviorSpec({

    val creationApplication = mockk<CustomerReservationIntentCreationApplication>()
    val requestRepository = mockk<ReservationIntentRequestRepository>()
    val intentRepository = mockk<ReservationIntentRepository>()
    val paymentService = mockk<ReservationPaymentService>()
    val sut = CustomerReservationIntentIdempotencyApplication(
        creationApplication = creationApplication,
        requestRepository = requestRepository,
        intentRepository = intentRepository,
        paymentService = paymentService
    )

    val command = CustomerReservationIntentCommand(
        memberId = "member-1",
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        checkIn = LocalDate.of(2026, 4, 1),
        checkOut = LocalDate.of(2026, 4, 3),
        numberOfGuests = 2,
        guestName = "김고객",
        guestPhone = "010-1111-2222",
        guestEmail = "kim@test.com"
    )
    val intent = ReservationIntent.create(
        id = "intent-1",
        memberId = command.memberId,
        propertyId = command.propertyId,
        roomTypeId = command.roomTypeId,
        guestInfo = GuestInfo(command.guestName, command.guestPhone, command.guestEmail),
        dateRange = DateRange.of(command.checkIn, command.checkOut),
        numberOfGuests = command.numberOfGuests,
        channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
        paymentId = "pay-1",
        holdId = "hold-1",
        expiresAt = Instant.parse("2026-04-08T10:15:00Z"),
        now = Instant.parse("2026-04-08T10:00:00Z")
    )
    val payment = ReservationPaymentSnapshot(
        id = "pay-1",
        reservationId = null,
        reservationIntentId = "intent-1",
        memberId = command.memberId,
        orderId = "STAYOPS-intent-1-test",
        amount = Money.won(200_000),
        status = ReservationPaymentStatus.PENDING,
        paymentKey = null,
        failReason = null
    )
    val result = CustomerReservationIntentResult(intent, payment)

    given("같은 멱등성 키로 예약 intent 생성을 재요청하면") {
        val request = ReservationIntentRequest.create(
            memberId = command.memberId,
            idempotencyKey = "intent-key-1",
            requestHash = command.fingerprint(),
            reservationIntentId = intent.id,
            createdAt = Instant.parse("2026-04-08T10:00:00Z")
        )
        every { requestRepository.findByMemberIdAndIdempotencyKey(command.memberId, "intent-key-1") } returns request
        every { intentRepository.findById(intent.id) } returns intent
        every { paymentService.findByReservationIntentId(intent.id) } returns payment

        val replayed = sut.createReservationIntent("intent-key-1", command)

        then("새로 생성하지 않고 기존 intent와 payment를 반환한다") {
            replayed shouldBe result
            verify(exactly = 0) { creationApplication.createReservationIntent(any(), any(), any()) }
        }
    }

    given("같은 멱등성 키를 다른 요청 내용으로 재사용하면") {
        val request = ReservationIntentRequest.create(
            memberId = command.memberId,
            idempotencyKey = "intent-key-1",
            requestHash = "different-request-hash",
            reservationIntentId = intent.id,
            createdAt = Instant.parse("2026-04-08T10:00:00Z")
        )
        every { requestRepository.findByMemberIdAndIdempotencyKey(command.memberId, "intent-key-1") } returns request

        val exception = shouldThrow<ConflictException> {
            sut.createReservationIntent("intent-key-1", command)
        }

        then("키 재사용 충돌을 반환한다") {
            exception.code shouldBe "IDEMPOTENCY_KEY_REUSED"
            verify(exactly = 0) { creationApplication.createReservationIntent(any(), any(), any()) }
        }
    }

    given("동시에 같은 멱등성 키를 저장해 한 요청이 충돌하면") {
        val request = ReservationIntentRequest.create(
            memberId = command.memberId,
            idempotencyKey = "intent-key-1",
            requestHash = command.fingerprint(),
            reservationIntentId = intent.id,
            createdAt = Instant.parse("2026-04-08T10:00:00Z")
        )
        every { requestRepository.findByMemberIdAndIdempotencyKey(command.memberId, "intent-key-1") } returnsMany listOf(null, request)
        every {
            creationApplication.createReservationIntent("intent-key-1", command.fingerprint(), command)
        } throws ReservationIntentRequestAlreadyExistsException()
        every { intentRepository.findById(intent.id) } returns intent
        every { paymentService.findByReservationIntentId(intent.id) } returns payment

        val replayed = sut.createReservationIntent("intent-key-1", command)

        then("승리한 요청이 저장한 기존 결과를 반환한다") {
            replayed shouldBe result
            verify(exactly = 1) { creationApplication.createReservationIntent(any(), any(), any()) }
        }
    }
})
