package com.stayops.reservation.infrastructure.persistence

import com.stayops.TestcontainersConfiguration
import com.stayops.reservation.domain.model.*
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MongoReservationRepositoryTest @Autowired constructor(
    private val reservationRepository: ReservationRepository,
    private val mongoDataRepository: ReservationMongoDataRepository
) {

    @BeforeEach
    fun setUp() {
        mongoDataRepository.deleteAll()
    }

    private fun newReservation(
        id: String = "rsv-1",
        propertyId: String = "prop-1",
        roomTypeId: String = "rt-1",
        guestId: String = "guest-1",
        checkIn: LocalDate = LocalDate.of(2026, 4, 1),
        checkOut: LocalDate = LocalDate.of(2026, 4, 3),
        channelCode: String = "DIRECT",
        commissionRate: BigDecimal = BigDecimal.ZERO
    ) = Reservation.create(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        guestId = guestId,
        guestInfo = GuestInfo(name = "홍길동", phone = "010-1234-5678", email = "hong@test.com"),
        dateRange = DateRange.of(checkIn, checkOut),
        numberOfGuests = 2,
        channel = ReservationChannel(channelCode = channelCode, commissionRate = commissionRate),
        pricing = ReservationPricing.calculate(
            roomRate = Money.won(200_000),
            additionalCharges = Money.ZERO,
            commissionRate = commissionRate
        )
    )

    @Nested
    inner class `save 및 findById` {
        @Test
        fun `저장 후 모든 필드가 보존된 도메인 객체를 조회한다`() {
            val reservation = newReservation()
            reservationRepository.save(reservation)

            val found = reservationRepository.findById("rsv-1")

            assertThat(found).isNotNull
            assertThat(found!!.propertyId).isEqualTo("prop-1")
            assertThat(found.roomTypeId).isEqualTo("rt-1")
            assertThat(found.guestInfo.name).isEqualTo("홍길동")
            assertThat(found.dateRange.checkIn).isEqualTo(LocalDate.of(2026, 4, 1))
            assertThat(found.nightCount).isEqualTo(2)
            assertThat(found.channel.channelCode).isEqualTo("DIRECT")
            assertThat(found.pricing.totalAmount).isEqualTo(Money.won(200_000))
            assertThat(found.pricing.commissionAmount).isEqualTo(Money.ZERO)
            assertThat(found.status).isEqualTo(ReservationStatus.PENDING)
        }

        @Test
        fun `OTA 예약의 수수료 정보가 보존된다`() {
            val reservation = newReservation(
                id = "rsv-2",
                channelCode = "AGODA",
                commissionRate = BigDecimal("0.15")
            )
            reservationRepository.save(reservation)

            val found = reservationRepository.findById("rsv-2")!!

            assertThat(found.channel.channelCode).isEqualTo("AGODA")
            assertThat(found.pricing.commissionAmount).isEqualTo(Money.won(30_000))
            assertThat(found.pricing.netAmount).isEqualTo(Money.won(170_000))
        }
    }

    @Nested
    inner class `findByPropertyIdAndStatus` {
        @Test
        fun `해당 상태의 예약만 반환한다`() {
            reservationRepository.save(newReservation(id = "rsv-1"))
            reservationRepository.save(newReservation(id = "rsv-2").confirm())

            val pending = reservationRepository.findByPropertyIdAndStatus("prop-1", ReservationStatus.PENDING)
            val confirmed = reservationRepository.findByPropertyIdAndStatus("prop-1", ReservationStatus.CONFIRMED)

            assertThat(pending).hasSize(1)
            assertThat(confirmed).hasSize(1)
        }
    }

    @Nested
    inner class `findByPropertyIdAndDateRange` {
        @Test
        fun `날짜 범위와 겹치는 예약을 반환한다`() {
            reservationRepository.save(newReservation(
                id = "rsv-1",
                checkIn = LocalDate.of(2026, 4, 1),
                checkOut = LocalDate.of(2026, 4, 3)
            ))
            reservationRepository.save(newReservation(
                id = "rsv-2",
                checkIn = LocalDate.of(2026, 4, 10),
                checkOut = LocalDate.of(2026, 4, 12)
            ))

            val results = reservationRepository.findByPropertyIdAndDateRange(
                "prop-1",
                LocalDate.of(2026, 4, 2),
                LocalDate.of(2026, 4, 5)
            )

            assertThat(results).hasSize(1)
            assertThat(results[0].id).isEqualTo("rsv-1")
        }
    }

    @Nested
    inner class `findByPropertyIdAndGuestId` {
        @Test
        fun `해당 고객의 예약을 반환한다`() {
            reservationRepository.save(newReservation(id = "rsv-1", guestId = "guest-1"))
            reservationRepository.save(newReservation(id = "rsv-2", guestId = "guest-2"))

            val results = reservationRepository.findByPropertyIdAndGuestId("prop-1", "guest-1")

            assertThat(results).hasSize(1)
        }
    }

    @Nested
    inner class `findByPropertyIdAndChannelCode` {
        @Test
        fun `해당 채널의 예약을 반환한다`() {
            reservationRepository.save(newReservation(id = "rsv-1", channelCode = "DIRECT"))
            reservationRepository.save(newReservation(
                id = "rsv-2",
                channelCode = "AGODA",
                commissionRate = BigDecimal("0.15")
            ))

            val directResults = reservationRepository.findByPropertyIdAndChannelCode("prop-1", "DIRECT")
            val otaResults = reservationRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA")

            assertThat(directResults).hasSize(1)
            assertThat(otaResults).hasSize(1)
        }
    }

    @Nested
    inner class `countByPropertyIdAndCreatedDate` {
        @Test
        fun `해당 날짜에 생성된 예약 수를 반환한다`() {
            reservationRepository.save(newReservation(id = "rsv-1"))
            reservationRepository.save(newReservation(id = "rsv-2"))
            reservationRepository.save(newReservation(id = "rsv-3", propertyId = "prop-2"))

            val today = LocalDate.now()
            val count = reservationRepository.countByPropertyIdAndCreatedDate("prop-1", today)

            assertThat(count).isEqualTo(2)
        }

        @Test
        fun `다른 날짜에 생성된 예약은 포함하지 않는다`() {
            reservationRepository.save(newReservation(id = "rsv-1"))

            val tomorrow = LocalDate.now().plusDays(1)
            val count = reservationRepository.countByPropertyIdAndCreatedDate("prop-1", tomorrow)

            assertThat(count).isEqualTo(0)
        }
    }
}
