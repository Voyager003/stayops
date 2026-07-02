package com.stayops.reservation.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.RESERVATION_INTENTS
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringJUnitConfig(classes = [RdbReservationIntentRepositoryTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbReservationIntentRepositoryTest @Autowired constructor(
    private val repository: ReservationIntentRepository,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, RdbReservationIntentRepository::class)
    class TestApplication

    private val now = Instant.parse("2026-04-01T01:00:00Z")
    private val checkIn = LocalDate.of(2026, 5, 1)
    private val checkOut = LocalDate.of(2026, 5, 3)

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(RESERVATION_INTENTS).execute()
        dsl.deleteFrom(ROOM_TYPES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
    }

    @Nested
    inner class `save 및 findById` {
        @Test
        fun `저장 후 모든 필드가 보존된 ReservationIntent를 조회한다`() {
            insertDependencies()

            repository.save(newIntent())

            val found = repository.findById("intent-1")
            assertThat(found).isNotNull
            assertThat(found!!.memberId).isEqualTo("member-1")
            assertThat(found.propertyId).isEqualTo("prop-1")
            assertThat(found.roomTypeId).isEqualTo("rt-1")
            assertThat(found.guestInfo.name).isEqualTo("홍길동")
            assertThat(found.dateRange.checkIn).isEqualTo(checkIn)
            assertThat(found.pricing.totalAmount).isEqualTo(Money.of(BigDecimal("200000.00")))
            assertThat(found.paymentId).isEqualTo("payment-1")
            assertThat(found.holdId).isEqualTo("hold-1")
            assertThat(found.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)
        }
    }

    @Nested
    inner class `active 중복 확인` {
        @Test
        fun `만료되지 않은 결제 대기 intent가 있으면 true를 반환한다`() {
            insertDependencies()
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
            insertDependencies()
            repository.save(newIntent(id = "intent-expired", expiresAt = now.minusSeconds(1)))
            repository.save(
                newIntent(id = "intent-reserved")
                    .requestPaymentConfirmation(now.minusSeconds(30))
                    .failPayment("PG 승인 실패", now.minusSeconds(10))
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

    private fun insertDependencies() {
        val timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC)
        dsl.insertInto(MEMBERS)
            .set(MEMBERS.ID, "member-1")
            .set(MEMBERS.EMAIL, "member@test.com")
            .set(MEMBERS.PASSWORD_HASH, "hash")
            .set(MEMBERS.NAME, "member")
            .set(MEMBERS.ROLE, MemberRole.OWNER.name)
            .set(MEMBERS.STATUS, MemberStatus.ACTIVE.name)
            .set(MEMBERS.VERSION, 0L)
            .set(MEMBERS.CREATED_AT, timestamp)
            .set(MEMBERS.UPDATED_AT, timestamp)
            .execute()
        dsl.insertInto(PROPERTIES)
            .set(PROPERTIES.ID, "prop-1")
            .set(PROPERTIES.OWNER_ID, "member-1")
            .set(PROPERTIES.NAME, "Stay Ops Hotel")
            .set(PROPERTIES.TYPE, "HOTEL")
            .set(PROPERTIES.ADDRESS_STREET, "1 Test Street")
            .set(PROPERTIES.ADDRESS_CITY, "Seoul")
            .set(PROPERTIES.ADDRESS_STATE, "Seoul")
            .set(PROPERTIES.ADDRESS_ZIP_CODE, "00000")
            .set(PROPERTIES.ADDRESS_COUNTRY, "KR")
            .set(PROPERTIES.CONTACT_PHONE, "010-0000-0000")
            .set(PROPERTIES.CONTACT_EMAIL, "property@stayops.com")
            .set(PROPERTIES.DESCRIPTION, "test property")
            .set(PROPERTIES.STATUS, "ACTIVE")
            .set(PROPERTIES.TIMEZONE, "Asia/Seoul")
            .set(PROPERTIES.CURRENCY, "KRW")
            .set(PROPERTIES.VERSION, 0L)
            .set(PROPERTIES.CREATED_AT, timestamp)
            .set(PROPERTIES.UPDATED_AT, timestamp)
            .execute()
        dsl.insertInto(ROOM_TYPES)
            .set(ROOM_TYPES.ID, "rt-1")
            .set(ROOM_TYPES.PROPERTY_ID, "prop-1")
            .set(ROOM_TYPES.NAME, "디럭스")
            .set(ROOM_TYPES.DESCRIPTION, "test room type")
            .set(ROOM_TYPES.MAX_OCCUPANCY, 2)
            .set(ROOM_TYPES.BASE_PRICE_AMOUNT, BigDecimal("150000.00"))
            .set(ROOM_TYPES.BASE_PRICE_CURRENCY, "KRW")
            .set(ROOM_TYPES.VERSION, 0L)
            .set(ROOM_TYPES.CREATED_AT, timestamp)
            .set(ROOM_TYPES.UPDATED_AT, timestamp)
            .execute()
    }
}
