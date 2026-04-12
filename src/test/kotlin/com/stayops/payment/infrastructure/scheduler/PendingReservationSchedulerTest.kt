package com.stayops.payment.infrastructure.scheduler

import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.reservation.domain.model.*
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PendingReservationSchedulerTest : BehaviorSpec({

    val reservationRepository = mockk<ReservationRepository>()
    val paymentRepository = mockk<PaymentRepository>()
    val inventoryReservationPort = mockk<InventoryReservationPort>()
    val clock = Clock.fixed(Instant.parse("2026-04-08T10:00:00Z"), ZoneId.of("Asia/Seoul"))

    val scheduler = PendingReservationScheduler(
        reservationRepository = reservationRepository,
        paymentRepository = paymentRepository,
        inventoryReservationPort = inventoryReservationPort,
        clock = clock
    )

    val checkIn = LocalDate.of(2026, 4, 1)
    val checkOut = LocalDate.of(2026, 4, 3)

    fun expiredReservation(id: String) = Reservation.create(
        id = id, propertyId = "prop-1", roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = GuestInfo("김고객", "010-1111-2222", null),
        dateRange = DateRange.of(checkIn, checkOut),
        numberOfGuests = 2,
        channel = BookingChannel("DIRECT", commissionRate = BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
        memberId = "member-1",
        expiresAt = Instant.now().minusSeconds(60) // 1분 전 만료
    )

    given("PENDING TTL 스케줄러 실행 시") {

        `when`("만료된 PENDING 예약이 있으면") {
            clearAllMocks()
            val expired1 = expiredReservation("rsv-1")
            val expired2 = expiredReservation("rsv-2")
            val payment1 = Payment.create(id = "pay-1", reservationId = "rsv-1", memberId = "member-1", amount = Money.won(200_000))
            val payment2 = Payment.create(id = "pay-2", reservationId = "rsv-2", memberId = "member-1", amount = Money.won(200_000))

            every { reservationRepository.findExpiredPending(any()) } returns listOf(expired1, expired2)
            every { paymentRepository.findByReservationId("rsv-1") } returns payment1
            every { paymentRepository.findByReservationId("rsv-2") } returns payment2
            every { reservationRepository.save(any()) } answers { firstArg() }
            every { paymentRepository.save(any()) } answers { firstArg() }
            justRun { inventoryReservationPort.release(any(), any(), any()) }

            scheduler.expirePendingReservations()

            then("예약이 CANCELLED되고 결제가 FAILED되고 재고가 복원된다") {
                verify(exactly = 2) { reservationRepository.save(match { it.status == ReservationStatus.CANCELLED }) }
                verify(exactly = 2) { paymentRepository.save(match { it.status == PaymentStatus.FAILED }) }
                // 2건 × 2박 = 4회 release
                verify(exactly = 4) { inventoryReservationPort.release("prop-1", "rt-1", any()) }
            }
        }

        `when`("만료된 PENDING 예약이 없으면") {
            clearAllMocks()
            every { reservationRepository.findExpiredPending(any()) } returns emptyList()

            scheduler.expirePendingReservations()

            then("아무 작업도 하지 않는다") {
                verify(exactly = 0) { reservationRepository.save(any()) }
                verify(exactly = 0) { paymentRepository.save(any()) }
            }
        }
    }
})
