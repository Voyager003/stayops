package com.stayops.reservation.application.service

import com.stayops.reservation.domain.event.ReservationCheckedOut
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomStatus
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import io.kotest.assertions.throwables.shouldThrow
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

class StayOperationApplicationTest : BehaviorSpec({

    val reservationRepository = mockk<ReservationRepository>()
    val roomRepository = mockk<RoomRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val fixedClock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("Asia/Seoul"))

    val sut = StayOperationApplication(
        reservationRepository = reservationRepository,
        roomRepository = roomRepository,
        eventPublisher = eventPublisher,
        clock = fixedClock
    )

    val checkIn = LocalDate.of(2026, 4, 1)
    val checkOut = LocalDate.of(2026, 4, 3)

    fun reservation(id: String, dateOffsetDays: Long = 0): Reservation =
        Reservation.create(
            id = id,
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            guestId = "guest-1",
            guestInfo = GuestInfo("홍길동", "010-1234-5678", null),
            dateRange = DateRange.of(checkIn.plusDays(dateOffsetDays), checkOut.plusDays(dateOffsetDays)),
            numberOfGuests = 2,
            channel = ReservationChannel("DIRECT", null, BigDecimal.ZERO),
            pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
        )

    fun room(status: RoomStatus = RoomStatus.AVAILABLE): Room {
        val available = Room.create(
            id = "room-101",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            roomNumber = "101",
            floor = 1
        )
        return if (status == RoomStatus.OCCUPIED) available.checkIn() else available
    }

    given("체크인 운영은") {
        `when`("CONFIRMED 예약에 AVAILABLE 객실을 배정하면") {
            then("예약은 CHECKED_IN, 객실은 OCCUPIED 상태가 된다") {
                clearAllMocks()
                val reservation = reservation("rsv-ci1").confirm()
                every { reservationRepository.findById("rsv-ci1") } returns reservation
                every { roomRepository.findById("room-101") } returns room()
                every { roomRepository.save(any()) } answers { firstArg() }
                every { reservationRepository.save(any()) } answers { firstArg() }

                val result = sut.checkInReservation("prop-1", "rsv-ci1", "room-101")

                result.status shouldBe ReservationStatus.CHECKED_IN
                result.roomId shouldBe "room-101"
                verify { roomRepository.save(match { it.status == RoomStatus.OCCUPIED }) }
            }
        }

        `when`("체크인 날짜가 오늘이 아니면") {
            then("객실 조회 없이 거부한다") {
                clearAllMocks()
                every { reservationRepository.findById("rsv-ci-future") } returns
                    reservation("rsv-ci-future", dateOffsetDays = 1).confirm()

                val exception = shouldThrow<BusinessException> {
                    sut.checkInReservation("prop-1", "rsv-ci-future", "room-101")
                }

                exception.code shouldBe "CHECK_IN_NOT_ALLOWED_DATE"
                verify(exactly = 0) { roomRepository.findById(any()) }
                verify(exactly = 0) { roomRepository.save(any()) }
                verify(exactly = 0) { reservationRepository.save(any()) }
            }
        }

        `when`("배정하려는 객실이 이미 사용 중이면") {
            then("예약 상태를 변경하지 않는다") {
                clearAllMocks()
                every { reservationRepository.findById("rsv-ci-occupied") } returns
                    reservation("rsv-ci-occupied").confirm()
                every { roomRepository.findById("room-101") } returns room(RoomStatus.OCCUPIED)

                shouldThrow<IllegalStateException> {
                    sut.checkInReservation("prop-1", "rsv-ci-occupied", "room-101")
                }

                verify(exactly = 0) { roomRepository.save(any()) }
                verify(exactly = 0) { reservationRepository.save(any()) }
            }
        }
    }

    given("체크아웃 운영은") {
        `when`("CHECKED_IN 예약을 체크아웃하면") {
            then("예약은 CHECKED_OUT, 객실은 CLEANING 상태가 된다") {
                clearAllMocks()
                val checkedIn = reservation("rsv-co1").confirm().checkIn("room-101")
                every { reservationRepository.findById("rsv-co1") } returns checkedIn
                every { reservationRepository.save(any()) } answers { firstArg() }
                every { roomRepository.findById("room-101") } returns room(RoomStatus.OCCUPIED)
                every { roomRepository.save(any()) } answers { firstArg() }
                justRun { eventPublisher.publishEvent(any()) }

                val result = sut.checkOutReservation("prop-1", "rsv-co1")

                result.status shouldBe ReservationStatus.CHECKED_OUT
                verify { roomRepository.save(match { it.status == RoomStatus.CLEANING }) }
                verify { eventPublisher.publishEvent(any<ReservationCheckedOut>()) }
            }
        }
    }

    given("노쇼 운영은") {
        `when`("CONFIRMED 예약을 체크인 당일에 노쇼 처리하면") {
            then("NO_SHOW 상태가 된다") {
                clearAllMocks()
                every { reservationRepository.findById("rsv-noshow") } returns reservation("rsv-noshow").confirm()
                every { reservationRepository.save(any()) } answers { firstArg() }

                val result = sut.noShowReservation("prop-1", "rsv-noshow")

                result.status shouldBe ReservationStatus.NO_SHOW
            }
        }

        `when`("체크인 날짜가 오늘이 아니면") {
            then("저장하지 않고 거부한다") {
                clearAllMocks()
                every { reservationRepository.findById("rsv-noshow-future") } returns
                    reservation("rsv-noshow-future", dateOffsetDays = 1).confirm()

                val exception = shouldThrow<BusinessException> {
                    sut.noShowReservation("prop-1", "rsv-noshow-future")
                }

                exception.code shouldBe "NO_SHOW_NOT_ALLOWED_DATE"
                verify(exactly = 0) { reservationRepository.save(any()) }
            }
        }
    }
})
