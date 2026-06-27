package com.stayops.inventory.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.inventory.domain.model.InventoryHold
import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.jooq.generated.Tables.INVENTORY_HOLD_DATES
import com.stayops.jooq.generated.Tables.INVENTORY_HOLDS
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.RESERVATION_INTENTS
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
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

@SpringJUnitConfig(classes = [RdbInventoryHoldRepositoryTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbInventoryHoldRepositoryTest @Autowired constructor(
    private val repository: InventoryHoldRepository,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, RdbInventoryHoldRepository::class)
    class TestApplication

    private val now = Instant.parse("2026-04-01T01:00:00Z")
    private val dates = listOf(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2))

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(INVENTORY_HOLD_DATES).execute()
        dsl.deleteFrom(INVENTORY_HOLDS).execute()
        dsl.deleteFrom(RESERVATION_INTENTS).execute()
        dsl.deleteFrom(ROOM_TYPES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
    }

    @Nested
    inner class `save 및 findById` {
        @Test
        fun `저장 후 모든 필드가 보존된 InventoryHold를 조회한다`() {
            insertDependencies()

            repository.save(newHold())

            val found = repository.findById("hold-1")
            assertThat(found).isNotNull
            assertThat(found!!.reservationIntentId).isEqualTo("intent-1")
            assertThat(found.propertyId).isEqualTo("prop-1")
            assertThat(found.roomTypeId).isEqualTo("rt-1")
            assertThat(found.dates).containsExactlyElementsOf(dates)
            assertThat(found.quantity).isEqualTo(1)
            assertThat(found.status).isEqualTo(InventoryHoldStatus.HELD)
        }
    }

    @Nested
    inner class `active hold 조회` {
        @Test
        fun `겹치는 날짜의 만료되지 않은 hold만 반환한다`() {
            insertDependencies()
            repository.save(newHold(id = "hold-active"))
            repository.save(newHold(id = "hold-expired", expiresAt = now.minusSeconds(1)))
            repository.save(newHold(id = "hold-other-date", dates = listOf(LocalDate.of(2026, 5, 10))))
            repository.save(newHold(id = "hold-released").release(now.minusSeconds(1)))

            val result = repository.findActiveByPropertyIdAndRoomTypeIdAndDates(
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                dates = listOf(LocalDate.of(2026, 5, 2)),
                now = now
            )

            assertThat(result.map { it.id }).containsExactly("hold-active")
        }
    }

    private fun newHold(
        id: String = "hold-1",
        dates: List<LocalDate> = this.dates,
        expiresAt: Instant = now.plusSeconds(900)
    ): InventoryHold =
        InventoryHold.create(
            id = id,
            reservationIntentId = "intent-1",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            dates = dates,
            quantity = 1,
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
        dsl.insertInto(RESERVATION_INTENTS)
            .set(RESERVATION_INTENTS.ID, "intent-1")
            .set(RESERVATION_INTENTS.MEMBER_ID, "member-1")
            .set(RESERVATION_INTENTS.PROPERTY_ID, "prop-1")
            .set(RESERVATION_INTENTS.ROOM_TYPE_ID, "rt-1")
            .set(RESERVATION_INTENTS.GUEST_NAME, "홍길동")
            .set(RESERVATION_INTENTS.GUEST_PHONE, "010-1234-5678")
            .set(RESERVATION_INTENTS.CHECK_IN, LocalDate.of(2026, 5, 1))
            .set(RESERVATION_INTENTS.CHECK_OUT, LocalDate.of(2026, 5, 3))
            .set(RESERVATION_INTENTS.NIGHT_COUNT, 2)
            .set(RESERVATION_INTENTS.NUMBER_OF_GUESTS, 2)
            .set(RESERVATION_INTENTS.CHANNEL_CODE, "DIRECT")
            .set(RESERVATION_INTENTS.COMMISSION_RATE, BigDecimal.ZERO)
            .set(RESERVATION_INTENTS.ROOM_RATE_AMOUNT, BigDecimal("200000.00"))
            .set(RESERVATION_INTENTS.ROOM_RATE_CURRENCY, "KRW")
            .set(RESERVATION_INTENTS.ADDITIONAL_CHARGES_AMOUNT, BigDecimal.ZERO)
            .set(RESERVATION_INTENTS.TOTAL_AMOUNT, BigDecimal("200000.00"))
            .set(RESERVATION_INTENTS.COMMISSION_AMOUNT, BigDecimal.ZERO)
            .set(RESERVATION_INTENTS.NET_AMOUNT, BigDecimal("200000.00"))
            .set(RESERVATION_INTENTS.PAYMENT_ID, "payment-1")
            .set(RESERVATION_INTENTS.HOLD_ID, "hold-1")
            .set(RESERVATION_INTENTS.STATUS, "PAYMENT_WAITING")
            .set(RESERVATION_INTENTS.EXPIRES_AT, timestamp.plusMinutes(15))
            .set(RESERVATION_INTENTS.VERSION, 0L)
            .set(RESERVATION_INTENTS.CREATED_AT, timestamp)
            .set(RESERVATION_INTENTS.UPDATED_AT, timestamp)
            .execute()
    }
}
