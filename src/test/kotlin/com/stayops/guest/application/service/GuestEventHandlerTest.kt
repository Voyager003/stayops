package com.stayops.guest.application.service

import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.model.GuestTier
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.reservation.domain.event.ReservationCheckedOut
import com.stayops.shared.domain.Money
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*

class GuestEventHandlerTest : BehaviorSpec({

    val guestRepository = mockk<GuestRepository>()
    val sut = GuestEventHandler(guestRepository)

    given("ReservationCheckedOut 이벤트 수신 시") {
        `when`("고객이 존재하면") {
            then("방문 이력이 갱신되고 저장된다") {
                clearAllMocks()
                val guest = Guest.create(
                    id = "guest-1",
                    propertyId = "prop-1",
                    name = "홍길동",
                    phone = "010-1234-5678"
                )
                every { guestRepository.findById("guest-1") } returns guest
                every { guestRepository.save(any()) } answers { firstArg() }

                val event = ReservationCheckedOut(
                    reservationId = "rsv-1",
                    propertyId = "prop-1",
                    guestId = "guest-1",
                    totalAmount = Money.won(200_000),
                    stayNights = 2
                )

                sut.onReservationCheckedOut(event)

                verify {
                    guestRepository.save(match {
                        it.visitSummary.totalVisits == 1 &&
                        it.tier == GuestTier.NEW
                    })
                }
            }
        }

        `when`("고객이 존재하지 않으면") {
            then("저장하지 않는다") {
                clearAllMocks()
                every { guestRepository.findById("guest-999") } returns null

                val event = ReservationCheckedOut(
                    reservationId = "rsv-2",
                    propertyId = "prop-1",
                    guestId = "guest-999",
                    totalAmount = Money.won(100_000),
                    stayNights = 1
                )

                sut.onReservationCheckedOut(event)

                verify(exactly = 0) { guestRepository.save(any()) }
            }
        }
    }
})
