package com.stayops.reservation.infrastructure.persistence.mongo

import com.stayops.TestcontainersConfiguration
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.reservation.infrastructure.persistence.mongo.dao.ReservationIntentMongoDao
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
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MongoReservationIntentRepositoryTest @Autowired constructor(
    private val repository: ReservationIntentRepository,
    private val mongoDao: ReservationIntentMongoDao
) {

    private val now = Instant.parse("2026-04-01T01:00:00Z")
    private val checkIn = LocalDate.of(2026, 5, 1)
    private val checkOut = LocalDate.of(2026, 5, 3)

    @BeforeEach
    fun setUp() {
        mongoDao.deleteAll()
    }

    @Nested
    inner class `save 및 findById` {
        @Test
        fun `저장 후 모든 필드가 보존된 ReservationIntent를 조회한다`() {
            val intent = newIntent()

            repository.save(intent)

            val found = repository.findById("intent-1")
            assertThat(found).isNotNull
            assertThat(found!!.memberId).isEqualTo("member-1")
            assertThat(found.propertyId).isEqualTo("prop-1")
            assertThat(found.roomTypeId).isEqualTo("rt-1")
            assertThat(found.guestInfo.name).isEqualTo("홍길동")
            assertThat(found.dateRange.checkIn).isEqualTo(checkIn)
            assertThat(found.pricing.totalAmount).isEqualTo(Money.won(200_000))
            assertThat(found.paymentId).isEqualTo("payment-1")
            assertThat(found.holdId).isEqualTo("hold-1")
            assertThat(found.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)
        }
    }

    @Nested
    inner class `existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut` {
        @Test
        fun `만료되지 않은 결제 대기 intent가 있으면 true를 반환한다`() {
            repository.save(newIntent())

            val exists = repository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
                memberId = "member-1",
                roomTypeId = "rt-1",
                checkIn = checkIn,
                checkOut = checkOut,
                now = now
            )

            assertThat(exists).isTrue()
        }

        @Test
        fun `만료되었거나 종료 상태인 intent는 active로 보지 않는다`() {
            repository.save(
                newIntent(id = "intent-expired", expiresAt = now.minusSeconds(1))
            )
            repository.save(
                newIntent(id = "intent-reserved")
                    .requestPaymentConfirmation(now.minusSeconds(30))
                    .markReserved("reservation-1", now.minusSeconds(10))
            )

            val exists = repository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
                memberId = "member-1",
                roomTypeId = "rt-1",
                checkIn = checkIn,
                checkOut = checkOut,
                now = now
            )

            assertThat(exists).isFalse()
        }
    }

    private fun newIntent(
        id: String = "intent-1",
        expiresAt: Instant = now.plusSeconds(900)
    ): ReservationIntent =
        ReservationIntent.create(
            id = id,
            memberId = "member-1",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            guestInfo = GuestInfo("홍길동", "010-1234-5678", "hong@test.com"),
            dateRange = DateRange.of(checkIn, checkOut),
            numberOfGuests = 2,
            channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
            pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
            paymentId = "payment-1",
            holdId = "hold-1",
            expiresAt = expiresAt,
            now = now.minusSeconds(10)
        )
}
