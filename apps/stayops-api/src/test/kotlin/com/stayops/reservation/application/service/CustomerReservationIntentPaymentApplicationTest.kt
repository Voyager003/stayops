package com.stayops.reservation.application.service

import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.application.required.ReservationPaymentSnapshot
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class CustomerReservationIntentPaymentApplicationTest : BehaviorSpec({

    val reservationIntentRepository = mockk<ReservationIntentRepository>()
    val reservationPaymentService = mockk<ReservationPaymentService>()
    val fixedInstant = Instant.parse("2026-04-08T10:00:00Z")
    val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    val sut = CustomerReservationIntentPaymentApplication(
        reservationIntentRepository = reservationIntentRepository,
        reservationPaymentService = reservationPaymentService,
        clock = clock
    )

    fun intent(): ReservationIntent =
        ReservationIntent.create(
            id = "intent-1",
            memberId = "member-1",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            guestInfo = GuestInfo("김고객", "010-1111-2222", "kim@test.com"),
            dateRange = DateRange.of(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3)),
            numberOfGuests = 2,
            channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
            pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
            paymentId = "pay-1",
            holdId = "hold-1",
            expiresAt = fixedInstant.plusSeconds(900),
            now = fixedInstant
        )

    fun payment(status: ReservationPaymentStatus = ReservationPaymentStatus.PENDING): ReservationPaymentSnapshot =
        ReservationPaymentSnapshot(
            id = "pay-1",
            reservationId = null,
            reservationIntentId = "intent-1",
            memberId = "member-1",
            orderId = "STAYOPS-intent-1-123",
            amount = Money.won(200_000),
            status = status,
            paymentKey = if (status == ReservationPaymentStatus.CONFIRM_REQUESTED) "toss_pk_123" else null,
            failReason = null
        )

    beforeTest {
        clearAllMocks()
    }

    given("예약 intent 결제 승인 요청 시") {
        `when`("예약 intent 결제 상태를 조회하면") {
            every { reservationIntentRepository.findById("intent-1") } returns intent()
            every { reservationPaymentService.findByReservationIntentId("intent-1") } returns
                payment(ReservationPaymentStatus.CONFIRM_REQUESTED)

            val result = sut.getPaymentStatus(
                memberId = "member-1",
                reservationIntentId = "intent-1"
            )

            then("intent와 payment 상태를 반환한다") {
                result.intent.id shouldBe "intent-1"
                result.payment.status shouldBe ReservationPaymentStatus.CONFIRM_REQUESTED
            }
        }

        `when`("이미 결제 실패 상태의 intent이면") {
            val failedIntent = intent()
                .requestPaymentConfirmation(fixedInstant.plusSeconds(1))
                .failPayment("PG 승인 실패", fixedInstant.plusSeconds(2))
            every { reservationIntentRepository.findById("intent-1") } returns failedIntent
            every { reservationPaymentService.findByReservationIntentId("intent-1") } returns
                payment(ReservationPaymentStatus.FAILED)

            val result = sut.confirmPayment(
                memberId = "member-1",
                reservationIntentId = "intent-1",
                paymentKey = "toss_pk_123",
                orderId = "STAYOPS-intent-1-123",
                amount = BigDecimal(200_000)
            )

            then("승인 요청을 다시 시작하지 않고 현재 실패 상태를 반환한다") {
                result.intent.status shouldBe ReservationIntentStatus.PAYMENT_FAILED
                result.payment.status shouldBe ReservationPaymentStatus.FAILED
                verify(exactly = 0) { reservationIntentRepository.save(any()) }
                verify(exactly = 0) { reservationPaymentService.requestConfirmForReservationIntent(any(), any(), any(), any(), any()) }
            }
        }

        `when`("결제 가능한 intent이면") {
            val savedIntent = slot<ReservationIntent>()
            every { reservationIntentRepository.findById("intent-1") } returns intent()
            every { reservationIntentRepository.save(capture(savedIntent)) } answers { firstArg() }
            every { reservationPaymentService.findByReservationIntentId("intent-1") } returns payment()
            every {
                reservationPaymentService.requestConfirmForReservationIntent(
                    reservationIntentId = "intent-1",
                    memberId = "member-1",
                    paymentKey = "toss_pk_123",
                    orderId = "STAYOPS-intent-1-123",
                    amount = BigDecimal(200_000)
                )
            } returns payment(ReservationPaymentStatus.CONFIRM_REQUESTED)

            val result = sut.confirmPayment(
                memberId = "member-1",
                reservationIntentId = "intent-1",
                paymentKey = "toss_pk_123",
                orderId = "STAYOPS-intent-1-123",
                amount = BigDecimal(200_000)
            )

            then("intent와 payment를 승인 요청 상태로 전환한다") {
                result.intent.status shouldBe ReservationIntentStatus.CONFIRM_REQUESTED
                result.payment.status shouldBe ReservationPaymentStatus.CONFIRM_REQUESTED
                savedIntent.captured.status shouldBe ReservationIntentStatus.CONFIRM_REQUESTED
            }
        }
    }
})
