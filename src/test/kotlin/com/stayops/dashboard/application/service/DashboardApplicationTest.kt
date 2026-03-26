package com.stayops.dashboard.application.service

import com.stayops.reservation.domain.model.*
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomStatus
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDate

class DashboardApplicationTest : BehaviorSpec({

    val reservationRepository = mockk<ReservationRepository>()
    val roomRepository = mockk<RoomRepository>()
    val sut = DashboardApplication(reservationRepository, roomRepository)

    val today = LocalDate.of(2026, 4, 1)

    fun sampleReservation(
        id: String,
        status: ReservationStatus,
        checkIn: LocalDate = today,
        checkOut: LocalDate = today.plusDays(2)
    ) = Reservation.create(
        id = id, propertyId = "prop-1", roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = GuestInfo("홍길동", "010-1234-5678", null),
        dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
        channel = BookingChannel("DIRECT", null, BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
    ).let {
        when (status) {
            ReservationStatus.CONFIRMED -> it.confirm()
            ReservationStatus.PENDING -> it
            else -> it
        }
    }

    given("대시보드 조회 시") {
        `when`("오늘의 체크인/아웃 예약과 객실 현황이 있으면") {
            then("운영 현황을 반환한다") {
                clearAllMocks()

                val checkInReservation = sampleReservation("rsv-1", ReservationStatus.CONFIRMED, checkIn = today)
                val checkOutReservation = sampleReservation("rsv-2", ReservationStatus.CONFIRMED, checkIn = today.minusDays(2), checkOut = today)

                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-1", today, today)
                } returns listOf(checkInReservation, checkOutReservation)

                every {
                    reservationRepository.findByPropertyIdAndStatus("prop-1", ReservationStatus.PENDING)
                } returns listOf(sampleReservation("rsv-3", ReservationStatus.PENDING))

                val rooms = listOf(
                    Room.create("r-1", "prop-1", "rt-1", "101", 1),
                    Room.create("r-2", "prop-1", "rt-1", "102", 1).checkIn(),
                    Room.create("r-3", "prop-1", "rt-1", "201", 2).checkIn()
                )
                every { roomRepository.findByPropertyId("prop-1") } returns rooms

                val result = sut.getDashboard("prop-1", today)

                result.todayCheckInCount shouldBe 1
                result.todayCheckOutCount shouldBe 1
                result.pendingReservations shouldBe 1
                result.occupancy.total shouldBe 3
                result.occupancy.occupied shouldBe 2
                result.occupancy.available shouldBe 1
            }
        }
    }
})
