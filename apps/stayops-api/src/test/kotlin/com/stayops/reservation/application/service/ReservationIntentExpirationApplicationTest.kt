package com.stayops.reservation.application.service

import com.stayops.inventory.application.provided.InventoryHoldService
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
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReservationIntentExpirationApplicationTest : BehaviorSpec({

    val reservationIntentRepository = mockk<ReservationIntentRepository>()
    val inventoryHoldService = mockk<InventoryHoldService>()
    val fixedInstant = Instant.parse("2026-04-08T10:16:00Z")
    val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    val sut = ReservationIntentExpirationApplication(
        reservationIntentRepository = reservationIntentRepository,
        inventoryHoldService = inventoryHoldService,
        clock = clock
    )

    fun expiredIntent(id: String): ReservationIntent =
        ReservationIntent.create(
            id = id,
            memberId = "member-1",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            guestInfo = GuestInfo("김고객", "010-1111-2222", "kim@test.com"),
            dateRange = DateRange.of(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3)),
            numberOfGuests = 2,
            channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
            pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
            paymentId = "pay-$id",
            holdId = "hold-$id",
            expiresAt = fixedInstant.minusSeconds(60),
            now = fixedInstant.minusSeconds(900)
        )

    beforeTest {
        clearAllMocks()
    }

    given("예약 intent 만료 처리 시") {
        `when`("결제 대기 상태의 만료된 intent가 있으면") {
            val savedIntent = slot<ReservationIntent>()
            val releasedIntentIds = mutableListOf<String>()
            every { reservationIntentRepository.findExpiredPaymentWaiting(fixedInstant, 100) } returns listOf(expiredIntent("intent-1"))
            every { reservationIntentRepository.save(capture(savedIntent)) } answers { firstArg() }
            every { inventoryHoldService.release(capture(releasedIntentIds)) } returns Unit

            val result = sut.expirePaymentWaitingIntents(limit = 100)

            then("intent를 EXPIRED로 변경하고 hold를 해제한다") {
                result shouldBe 1
                savedIntent.captured.status shouldBe ReservationIntentStatus.EXPIRED
                releasedIntentIds shouldBe listOf("intent-1")
            }
        }
    }
})
