package com.stayops.reservation.infrastructure.persistence

import com.stayops.TestcontainersConfiguration
import com.stayops.reservation.domain.model.*
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.time.StayopsTimeProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class MongoReservationRepositoryTest @Autowired constructor(
    private val reservationRepository: ReservationRepository,
    private val mongoDataRepository: ReservationMongoDataRepository,
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock,
    private val timeProperties: StayopsTimeProperties
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
        externalReservationId: String? = null,
        commissionRate: BigDecimal = BigDecimal.ZERO,
        memberId: String? = null,
        expiresAt: Instant? = null,
        createdAt: Instant? = null
    ): Reservation {
        val reservation = Reservation.create(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        guestId = guestId,
        guestInfo = GuestInfo(name = "홍길동", phone = "010-1234-5678", email = "hong@test.com"),
        dateRange = DateRange.of(checkIn, checkOut),
        numberOfGuests = 2,
        channel = ReservationChannel(
            channelCode = channelCode,
            externalReservationId = externalReservationId,
            commissionRate = commissionRate
        ),
        pricing = ReservationPricing.calculate(
            roomRate = Money.won(200_000),
            additionalCharges = Money.ZERO,
            commissionRate = commissionRate
        ),
        memberId = memberId,
        expiresAt = expiresAt
        )
        return if (createdAt == null) {
            reservation
        } else {
            Reservation.reconstitute(
                id = reservation.id,
                propertyId = reservation.propertyId,
                roomTypeId = reservation.roomTypeId,
                roomId = reservation.roomId,
                guestId = reservation.guestId,
                guestInfo = reservation.guestInfo,
                dateRange = reservation.dateRange,
                nightCount = reservation.nightCount,
                numberOfGuests = reservation.numberOfGuests,
                status = reservation.status,
                channel = reservation.channel,
                pricing = reservation.pricing,
                memberId = reservation.memberId,
                expiresAt = reservation.expiresAt,
                version = reservation.version,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        }
    }

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
    inner class `findByPropertyIdAndChannelCodeAndExternalReservationId` {
        @Test
        fun `OTA 외부 예약 ID와 일치하는 예약을 반환한다`() {
            reservationRepository.save(
                newReservation(
                    id = "rsv-agoda-1",
                    channelCode = "AGODA",
                    externalReservationId = "agoda-booking-1",
                    commissionRate = BigDecimal("0.15")
                ).confirm()
            )
            reservationRepository.save(
                newReservation(
                    id = "rsv-agoda-2",
                    channelCode = "AGODA",
                    externalReservationId = "agoda-booking-2",
                    commissionRate = BigDecimal("0.15")
                ).confirm()
            )
            reservationRepository.save(
                newReservation(
                    id = "rsv-expedia-1",
                    channelCode = "EXPEDIA",
                    externalReservationId = "agoda-booking-1",
                    commissionRate = BigDecimal("0.12")
                ).confirm()
            )

            val found = reservationRepository.findByPropertyIdAndChannelCodeAndExternalReservationId(
                propertyId = "prop-1",
                channelCode = "AGODA",
                externalReservationId = "agoda-booking-1"
            )

            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo("rsv-agoda-1")
            assertThat(found.channel.channelCode).isEqualTo("AGODA")
            assertThat(found.channel.externalReservationId).isEqualTo("agoda-booking-1")
        }

        @Test
        fun `일치하는 OTA 외부 예약 ID가 없으면 null을 반환한다`() {
            reservationRepository.save(
                newReservation(
                    id = "rsv-agoda-1",
                    channelCode = "AGODA",
                    externalReservationId = "agoda-booking-1",
                    commissionRate = BigDecimal("0.15")
                ).confirm()
            )

            val found = reservationRepository.findByPropertyIdAndChannelCodeAndExternalReservationId(
                propertyId = "prop-1",
                channelCode = "AGODA",
                externalReservationId = "missing-booking"
            )

            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut` {
        private val now = Instant.parse("2026-04-13T10:00:00Z")

        @Test
        fun `만료된 PENDING 예약은 중복 예약으로 보지 않는다`() {
            reservationRepository.save(
                newReservation(
                    memberId = "member-1",
                    expiresAt = now.minusSeconds(1)
                )
            )

            val exists = reservationRepository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
                memberId = "member-1",
                roomTypeId = "rt-1",
                checkIn = LocalDate.of(2026, 4, 1),
                checkOut = LocalDate.of(2026, 4, 3),
                now = now
            )

            assertThat(exists).isEqualTo(false)
        }

        @Test
        fun `만료되지 않은 PENDING 예약은 중복 예약으로 본다`() {
            reservationRepository.save(
                newReservation(
                    memberId = "member-1",
                    expiresAt = now.plusSeconds(1)
                )
            )

            val exists = reservationRepository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
                memberId = "member-1",
                roomTypeId = "rt-1",
                checkIn = LocalDate.of(2026, 4, 1),
                checkOut = LocalDate.of(2026, 4, 3),
                now = now
            )

            assertThat(exists).isEqualTo(true)
        }

        @Test
        fun `CONFIRMED 예약은 expiresAt이 지나도 중복 예약으로 본다`() {
            reservationRepository.save(
                newReservation(
                    memberId = "member-1",
                    expiresAt = now.minusSeconds(1)
                ).confirm()
            )

            val exists = reservationRepository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
                memberId = "member-1",
                roomTypeId = "rt-1",
                checkIn = LocalDate.of(2026, 4, 1),
                checkOut = LocalDate.of(2026, 4, 3),
                now = now
            )

            assertThat(exists).isEqualTo(true)
        }
    }

    @Nested
    inner class `findPageByMemberId` {
        @Test
        fun `memberId 예약을 최신 생성순으로 페이지 조회한다`() {
            reservationRepository.save(newReservation(
                id = "old",
                memberId = "member-1",
                createdAt = Instant.parse("2026-04-01T00:00:00Z")
            ))
            reservationRepository.save(newReservation(
                id = "new",
                memberId = "member-1",
                createdAt = Instant.parse("2026-04-03T00:00:00Z")
            ))
            reservationRepository.save(newReservation(
                id = "middle",
                memberId = "member-1",
                createdAt = Instant.parse("2026-04-02T00:00:00Z")
            ))
            reservationRepository.save(newReservation(
                id = "other-member",
                memberId = "member-2",
                createdAt = Instant.parse("2026-04-04T00:00:00Z")
            ))

            val result = reservationRepository.findPageByMemberId("member-1", page = 0, size = 2)

            assertThat(result.content.map { it.id }).containsExactly("new", "middle")
            assertThat(result.totalElements).isEqualTo(3)
            assertThat(result.page).isEqualTo(0)
            assertThat(result.size).isEqualTo(2)
            assertThat(result.totalPages).isEqualTo(2)
        }

        @Test
        fun `memberId와 createdAt 복합 인덱스를 생성한다`() {
            val indexes = mongoTemplate.indexOps(ReservationDocument::class.java).indexInfo

            assertThat(indexes).anySatisfy { index ->
                assertThat(index.indexFields.map { it.key }).containsExactly("memberId", "createdAt")
            }
        }
    }

    @Nested
    inner class `countByPropertyIdAndCreatedDate` {
        @Test
        fun `해당 날짜에 생성된 예약 수를 반환한다`() {
            val today = LocalDate.now(clock)
            val createdAt = today.atStartOfDay(timeProperties.defaultZone()).toInstant()
            reservationRepository.save(newReservation(id = "rsv-1", createdAt = createdAt))
            reservationRepository.save(newReservation(id = "rsv-2", createdAt = createdAt))
            reservationRepository.save(newReservation(id = "rsv-3", propertyId = "prop-2", createdAt = createdAt))

            val count = reservationRepository.countByPropertyIdAndCreatedDate("prop-1", today)

            assertThat(count).isEqualTo(2)
        }

        @Test
        fun `다른 날짜에 생성된 예약은 포함하지 않는다`() {
            val today = LocalDate.now(clock)
            val createdAt = today.atStartOfDay(timeProperties.defaultZone()).toInstant()
            reservationRepository.save(newReservation(id = "rsv-1", createdAt = createdAt))

            val tomorrow = today.plusDays(1)
            val count = reservationRepository.countByPropertyIdAndCreatedDate("prop-1", tomorrow)

            assertThat(count).isEqualTo(0)
        }
    }
}
