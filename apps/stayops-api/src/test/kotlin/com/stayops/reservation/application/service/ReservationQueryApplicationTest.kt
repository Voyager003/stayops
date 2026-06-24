package com.stayops.reservation.application.service

import com.stayops.reservation.domain.model.DateType
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.PagedResult
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.LocalDate

class ReservationQueryApplicationTest : BehaviorSpec({

    val reservationRepository = mockk<ReservationRepository>()
    val sut = ReservationQueryApplication(reservationRepository)

    val checkIn = LocalDate.of(2026, 4, 1)
    val checkOut = LocalDate.of(2026, 4, 3)

    fun reservation(
        id: String = "rsv-1",
        propertyId: String = "prop-1",
        channelCode: String = "DIRECT",
        status: ReservationStatus = ReservationStatus.PENDING
    ): Reservation {
        val created = Reservation.create(
            id = id,
            propertyId = propertyId,
            roomTypeId = "rt-1",
            guestId = "guest-1",
            guestInfo = GuestInfo("홍길동", "010-1234-5678", null),
            dateRange = DateRange.of(checkIn, checkOut),
            numberOfGuests = 2,
            channel = ReservationChannel(channelCode, null, BigDecimal.ZERO),
            pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
        )
        return if (status == ReservationStatus.CONFIRMED) created.confirm() else created
    }

    given("예약 조회는") {
        `when`("예약이 요청한 숙소에 속하면") {
            then("예약을 반환한다") {
                clearAllMocks()
                every { reservationRepository.findById("rsv-1") } returns reservation()

                val result = sut.getReservation("prop-1", "rsv-1")

                result.id shouldBe "rsv-1"
                result.propertyId shouldBe "prop-1"
            }
        }

        `when`("예약이 존재하지 않으면") {
            then("NotFoundException을 던진다") {
                clearAllMocks()
                every { reservationRepository.findById("missing") } returns null

                val exception = shouldThrow<NotFoundException> {
                    sut.getReservation("prop-1", "missing")
                }

                exception.code shouldBe "RESERVATION_NOT_FOUND"
            }
        }

        `when`("예약이 다른 숙소에 속하면") {
            then("접근을 거부한다") {
                clearAllMocks()
                every { reservationRepository.findById("rsv-other") } returns reservation(propertyId = "prop-2")

                shouldThrow<IllegalArgumentException> {
                    sut.getReservation("prop-1", "rsv-other")
                }
            }
        }
    }

    given("예약 검색은") {
        `when`("상태와 채널 필터를 적용하면") {
            then("조건에 맞는 페이징 결과를 반환한다") {
                clearAllMocks()
                val criteria = ReservationSearchCriteria(
                    statuses = listOf(ReservationStatus.CONFIRMED),
                    channelCodes = listOf("AGODA"),
                    dateType = DateType.CHECK_IN,
                    startDate = checkIn,
                    endDate = checkOut
                )
                val reservation = reservation(
                    id = "rsv-s1",
                    channelCode = "AGODA",
                    status = ReservationStatus.CONFIRMED
                )
                every {
                    reservationRepository.search("prop-1", criteria, 0, 20)
                } returns PagedResult(
                    content = listOf(reservation),
                    totalElements = 1,
                    page = 0,
                    size = 20,
                    totalPages = 1
                )

                val result = sut.searchReservations("prop-1", criteria, 0, 20)

                result.totalElements shouldBe 1
                result.content[0].channel.channelCode shouldBe "AGODA"
                result.content[0].status shouldBe ReservationStatus.CONFIRMED
            }
        }

        `when`("여러 숙소 범위로 검색하면") {
            then("접근 가능한 숙소 목록을 기준으로 검색한다") {
                clearAllMocks()
                val criteria = ReservationSearchCriteria(statuses = listOf(ReservationStatus.CONFIRMED))
                every {
                    reservationRepository.searchByPropertyIds(listOf("prop-1", "prop-2"), criteria, 0, 20)
                } returns PagedResult(
                    content = listOf(reservation(propertyId = "prop-2", status = ReservationStatus.CONFIRMED)),
                    totalElements = 1,
                    page = 0,
                    size = 20,
                    totalPages = 1
                )

                val result = sut.searchReservationsByPropertyIds(listOf("prop-1", "prop-2"), criteria, 0, 20)

                result.totalElements shouldBe 1
                result.content[0].propertyId shouldBe "prop-2"
            }
        }
    }
})
