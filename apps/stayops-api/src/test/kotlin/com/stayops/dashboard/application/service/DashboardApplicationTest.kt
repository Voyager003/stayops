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
    val yesterday = today.minusDays(1)

    fun sampleReservation(
        id: String,
        status: ReservationStatus,
        checkIn: LocalDate = today,
        checkOut: LocalDate = today.plusDays(2),
        totalAmount: Long = 200_000
    ) = Reservation.create(
        id = id, propertyId = "prop-1", roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = GuestInfo("홍길동", "010-1234-5678", null),
        dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
        channel = ReservationChannel("DIRECT", null, BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(totalAmount), Money.ZERO, BigDecimal.ZERO)
    ).let {
        when (status) {
            ReservationStatus.CONFIRMED -> it.confirm()
            ReservationStatus.PENDING -> it
            else -> it
        }
    }

    given("대시보드 조회 시") {
        `when`("오늘과 전일의 체크인/아웃 예약, 매출, 신규 예약이 있으면") {
            then("오늘과 전일 운영 현황을 모두 반환한다") {
                clearAllMocks()

                val todayCheckIn = sampleReservation("rsv-1", ReservationStatus.CONFIRMED, checkIn = today, totalAmount = 135_000)
                val todayCheckOut = sampleReservation("rsv-2", ReservationStatus.CONFIRMED, checkIn = today.minusDays(2), checkOut = today, totalAmount = 100_000)
                val todayCheckIn2 = sampleReservation("rsv-3", ReservationStatus.CONFIRMED, checkIn = today, totalAmount = 215_000)

                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-1", today, today)
                } returns listOf(todayCheckIn, todayCheckOut, todayCheckIn2)

                val yesterdayCheckIn = sampleReservation("rsv-4", ReservationStatus.CONFIRMED, checkIn = yesterday, totalAmount = 300_000)

                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-1", yesterday, yesterday)
                } returns listOf(yesterdayCheckIn)

                every {
                    reservationRepository.findByPropertyIdAndStatus("prop-1", ReservationStatus.PENDING)
                } returns listOf(sampleReservation("rsv-5", ReservationStatus.PENDING))

                every {
                    reservationRepository.countByPropertyIdAndCreatedDate("prop-1", today)
                } returns 3

                every {
                    reservationRepository.countByPropertyIdAndCreatedDate("prop-1", yesterday)
                } returns 1

                val rooms = listOf(
                    Room.create("r-1", "prop-1", "rt-1", "101", 1),
                    Room.create("r-2", "prop-1", "rt-1", "102", 1).checkIn(),
                    Room.create("r-3", "prop-1", "rt-1", "201", 2).checkIn()
                )
                every { roomRepository.findByPropertyId("prop-1") } returns rooms

                val result = sut.getDashboard("prop-1", today)

                result.todayCheckInCount shouldBe 2
                result.todayCheckOutCount shouldBe 1
                result.todayRevenue shouldBe 350_000
                result.todayNewReservations shouldBe 3
                result.yesterdayCheckInCount shouldBe 1
                result.yesterdayCheckOutCount shouldBe 0
                result.yesterdayRevenue shouldBe 300_000
                result.yesterdayNewReservations shouldBe 1
                result.pendingReservations shouldBe 1
                result.occupancy.total shouldBe 3
                result.occupancy.occupied shouldBe 2
                result.occupancy.available shouldBe 1
            }
        }

        `when`("취소/노쇼 예약은 매출에서 제외한다") {
            then("취소된 예약의 금액은 매출에 포함되지 않는다") {
                clearAllMocks()

                val confirmedReservation = sampleReservation("rsv-1", ReservationStatus.CONFIRMED, checkIn = today, totalAmount = 200_000)
                val cancelledReservation = sampleReservation("rsv-2", ReservationStatus.PENDING, checkIn = today, totalAmount = 150_000)
                    // PENDING->CANCELLED via cancelPending
                val cancelledRes = cancelledReservation.cancelPending()

                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-1", today, today)
                } returns listOf(confirmedReservation, cancelledRes)

                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-1", yesterday, yesterday)
                } returns emptyList()

                every {
                    reservationRepository.findByPropertyIdAndStatus("prop-1", ReservationStatus.PENDING)
                } returns emptyList()

                every {
                    reservationRepository.countByPropertyIdAndCreatedDate("prop-1", today)
                } returns 0

                every {
                    reservationRepository.countByPropertyIdAndCreatedDate("prop-1", yesterday)
                } returns 0

                every { roomRepository.findByPropertyId("prop-1") } returns emptyList()

                val result = sut.getDashboard("prop-1", today)

                result.todayRevenue shouldBe 200_000
            }
        }

        `when`("예약이 없으면") {
            then("모든 값이 0으로 반환된다") {
                clearAllMocks()

                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-1", today, today)
                } returns emptyList()

                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-1", yesterday, yesterday)
                } returns emptyList()

                every {
                    reservationRepository.findByPropertyIdAndStatus("prop-1", ReservationStatus.PENDING)
                } returns emptyList()

                every {
                    reservationRepository.countByPropertyIdAndCreatedDate("prop-1", today)
                } returns 0

                every {
                    reservationRepository.countByPropertyIdAndCreatedDate("prop-1", yesterday)
                } returns 0

                every { roomRepository.findByPropertyId("prop-1") } returns emptyList()

                val result = sut.getDashboard("prop-1", today)

                result.todayCheckInCount shouldBe 0
                result.todayCheckOutCount shouldBe 0
                result.todayRevenue shouldBe 0
                result.todayNewReservations shouldBe 0
                result.yesterdayCheckInCount shouldBe 0
                result.yesterdayCheckOutCount shouldBe 0
                result.yesterdayRevenue shouldBe 0
                result.yesterdayNewReservations shouldBe 0
                result.occupancy.total shouldBe 0
                result.occupancy.rate shouldBe 0.0
            }
        }
    }

    given("모든 숙소 대시보드 조회 시") {
        `when`("여러 숙소의 데이터를 합산하면") {
            then("각 숙소의 수치를 합산한 결과를 반환한다") {
                clearAllMocks()

                // prop-1: 체크인 1건, 매출 135,000
                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-1", today, today)
                } returns listOf(sampleReservation("rsv-1", ReservationStatus.CONFIRMED, checkIn = today, totalAmount = 135_000))
                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-1", yesterday, yesterday)
                } returns emptyList()
                every { reservationRepository.findByPropertyIdAndStatus("prop-1", ReservationStatus.PENDING) } returns emptyList()
                every { reservationRepository.countByPropertyIdAndCreatedDate("prop-1", today) } returns 1
                every { reservationRepository.countByPropertyIdAndCreatedDate("prop-1", yesterday) } returns 0
                every { roomRepository.findByPropertyId("prop-1") } returns listOf(
                    Room.create("r-1", "prop-1", "rt-1", "101", 1)
                )

                // prop-2: 체크인 2건, 매출 400,000
                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-2", today, today)
                } returns listOf(
                    sampleReservation("rsv-2", ReservationStatus.CONFIRMED, checkIn = today, totalAmount = 200_000),
                    sampleReservation("rsv-3", ReservationStatus.CONFIRMED, checkIn = today, totalAmount = 200_000)
                )
                every {
                    reservationRepository.findByPropertyIdAndDateRange("prop-2", yesterday, yesterday)
                } returns emptyList()
                every { reservationRepository.findByPropertyIdAndStatus("prop-2", ReservationStatus.PENDING) } returns emptyList()
                every { reservationRepository.countByPropertyIdAndCreatedDate("prop-2", today) } returns 2
                every { reservationRepository.countByPropertyIdAndCreatedDate("prop-2", yesterday) } returns 0
                every { roomRepository.findByPropertyId("prop-2") } returns listOf(
                    Room.create("r-2", "prop-2", "rt-2", "201", 2).checkIn()
                )

                val result = sut.getAggregatedDashboard(listOf("prop-1", "prop-2"), today)

                result.todayCheckInCount shouldBe 3
                result.todayRevenue shouldBe 535_000
                result.todayNewReservations shouldBe 3
                result.occupancy.total shouldBe 2
                result.occupancy.occupied shouldBe 1
                result.occupancy.available shouldBe 1
            }
        }

        `when`("숙소 목록이 비어있으면") {
            then("모든 값이 0인 결과를 반환한다") {
                val result = sut.getAggregatedDashboard(emptyList(), today)

                result.todayCheckInCount shouldBe 0
                result.todayRevenue shouldBe 0
                result.occupancy.total shouldBe 0
            }
        }
    }
})
