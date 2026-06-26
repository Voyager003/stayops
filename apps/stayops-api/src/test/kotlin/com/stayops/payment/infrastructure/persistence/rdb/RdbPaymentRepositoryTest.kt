package com.stayops.payment.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.jooq.generated.Tables.GUESTS
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PAYMENTS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.RESERVATIONS
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentRepository
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

@SpringJUnitConfig(classes = [RdbPaymentRepositoryTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbPaymentRepositoryTest @Autowired constructor(
    private val paymentRepository: PaymentRepository,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, RdbPaymentRepository::class)
    class TestApplication

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(PAYMENTS).execute()
        dsl.deleteFrom(RESERVATIONS).execute()
        dsl.deleteFrom(GUESTS).execute()
        dsl.deleteFrom(ROOM_TYPES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
    }

    @Nested
    inner class `save_및_findById` {

        @Test
        fun `저장 후 모든 필드가 보존된 Payment를 조회한다`() {
            insertReservation("res-1")
            val payment = newPayment()

            paymentRepository.save(payment)

            val found = paymentRepository.findById("pay-1")
            assertThat(found).isNotNull
            assertThat(found!!.reservationId).isEqualTo("res-1")
            assertThat(found.memberId).isEqualTo("member-1")
            assertThat(found.orderId).isEqualTo("order-1")
            assertThat(found.amount).isEqualTo(Money.of(BigDecimal("220000.00")))
            assertThat(found.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(found.paymentKey).isEqualTo("payment-key-1")
            assertThat(found.method).isEqualTo("CARD")
            assertThat(found.approvedAt).isEqualTo(Instant.parse("2026-07-01T01:00:00Z"))
        }
    }

    @Nested
    inner class `조회` {

        @Test
        fun `예약 ID로 Payment를 조회한다`() {
            insertReservation("res-1")
            paymentRepository.save(newPayment())

            val found = paymentRepository.findByReservationId("res-1")

            assertThat(found!!.id).isEqualTo("pay-1")
        }

        @Test
        fun `예약 ID 목록으로 Payment 목록을 조회한다`() {
            insertReservation("res-1")
            insertReservation("res-2")
            paymentRepository.save(newPayment(id = "pay-1", reservationId = "res-1", orderId = "order-1"))
            paymentRepository.save(newPayment(id = "pay-2", reservationId = "res-2", orderId = "order-2"))

            val result = paymentRepository.findByReservationIds(listOf("res-1", "res-2"))

            assertThat(result.map { it.id }).containsExactly("pay-1", "pay-2")
        }

        @Test
        fun `빈 예약 ID 목록이면 빈 목록을 반환한다`() {
            val result = paymentRepository.findByReservationIds(emptyList())

            assertThat(result).isEmpty()
        }

        @Test
        fun `회원 ID로 Payment 목록을 조회한다`() {
            insertReservation("res-1")
            insertReservation("res-2")
            paymentRepository.save(newPayment(id = "pay-1", reservationId = "res-1", orderId = "order-1"))
            paymentRepository.save(newPayment(id = "pay-2", reservationId = "res-2", orderId = "order-2"))

            val result = paymentRepository.findByMemberId("member-1")

            assertThat(result.map { it.id }).containsExactly("pay-1", "pay-2")
        }

        @Test
        fun `주문 ID로 Payment를 조회한다`() {
            insertReservation("res-1")
            paymentRepository.save(newPayment(orderId = "order-1"))

            val found = paymentRepository.findByOrderId("order-1")

            assertThat(found!!.id).isEqualTo("pay-1")
        }
    }

    private fun newPayment(
        id: String = "pay-1",
        reservationId: String = "res-1",
        orderId: String = "order-1"
    ): Payment =
        Payment.reconstitute(
            id = id,
            reservationId = reservationId,
            memberId = "member-1",
            orderId = orderId,
            amount = Money.of(220_000),
            status = PaymentStatus.APPROVED,
            paymentKey = "payment-key-1",
            method = "CARD",
            failReason = null,
            approvedAt = Instant.parse("2026-07-01T01:00:00Z"),
            version = 0L,
            createdAt = Instant.parse("2026-07-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-07-01T01:00:00Z")
        )

    private fun insertReservation(reservationId: String) {
        insertMember("member-1")
        insertProperty("prop-1")
        insertRoomType("rt-1")
        insertGuest("guest-1")
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dsl.insertInto(RESERVATIONS)
            .set(RESERVATIONS.ID, reservationId)
            .set(RESERVATIONS.PROPERTY_ID, "prop-1")
            .set(RESERVATIONS.ROOM_TYPE_ID, "rt-1")
            .set(RESERVATIONS.GUEST_ID, "guest-1")
            .set(RESERVATIONS.GUEST_NAME, "김고객")
            .set(RESERVATIONS.GUEST_PHONE, "010-0000-0000")
            .set(RESERVATIONS.GUEST_EMAIL, "guest@stayops.com")
            .set(RESERVATIONS.CHECK_IN, LocalDate.of(2026, 7, 1))
            .set(RESERVATIONS.CHECK_OUT, LocalDate.of(2026, 7, 3))
            .set(RESERVATIONS.NIGHT_COUNT, 2)
            .set(RESERVATIONS.NUMBER_OF_GUESTS, 2)
            .set(RESERVATIONS.STATUS, "CONFIRMED")
            .set(RESERVATIONS.CHANNEL_CODE, "DIRECT")
            .set(RESERVATIONS.COMMISSION_RATE, BigDecimal("0.10000"))
            .set(RESERVATIONS.ROOM_RATE_AMOUNT, BigDecimal("200000.00"))
            .set(RESERVATIONS.ROOM_RATE_CURRENCY, "KRW")
            .set(RESERVATIONS.ADDITIONAL_CHARGES_AMOUNT, BigDecimal("20000.00"))
            .set(RESERVATIONS.TOTAL_AMOUNT, BigDecimal("220000.00"))
            .set(RESERVATIONS.COMMISSION_AMOUNT, BigDecimal("22000.00"))
            .set(RESERVATIONS.NET_AMOUNT, BigDecimal("198000.00"))
            .set(RESERVATIONS.MEMBER_ID, "member-1")
            .set(RESERVATIONS.VERSION, 0L)
            .set(RESERVATIONS.CREATED_AT, now)
            .set(RESERVATIONS.UPDATED_AT, now)
            .onConflictDoNothing()
            .execute()
    }

    private fun insertMember(memberId: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(MEMBERS)
            .set(MEMBERS.ID, memberId)
            .set(MEMBERS.EMAIL, "$memberId@stayops.com")
            .set(MEMBERS.PASSWORD_HASH, "hashed-password")
            .set(MEMBERS.NAME, memberId)
            .set(MEMBERS.ROLE, MemberRole.OWNER.name)
            .set(MEMBERS.STATUS, MemberStatus.ACTIVE.name)
            .set(MEMBERS.VERSION, 0L)
            .set(MEMBERS.CREATED_AT, now)
            .set(MEMBERS.UPDATED_AT, now)
            .onConflictDoNothing()
            .execute()
    }

    private fun insertProperty(propertyId: String) {
        val ownerId = "owner-$propertyId"
        insertMember(ownerId)
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dsl.insertInto(PROPERTIES)
            .set(PROPERTIES.ID, propertyId)
            .set(PROPERTIES.OWNER_ID, ownerId)
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
            .set(PROPERTIES.CREATED_AT, now)
            .set(PROPERTIES.UPDATED_AT, now)
            .onConflictDoNothing()
            .execute()
    }

    private fun insertRoomType(roomTypeId: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(ROOM_TYPES)
            .set(ROOM_TYPES.ID, roomTypeId)
            .set(ROOM_TYPES.PROPERTY_ID, "prop-1")
            .set(ROOM_TYPES.NAME, "디럭스")
            .set(ROOM_TYPES.DESCRIPTION, "test room type")
            .set(ROOM_TYPES.MAX_OCCUPANCY, 2)
            .set(ROOM_TYPES.BASE_PRICE_AMOUNT, BigDecimal("150000.00"))
            .set(ROOM_TYPES.BASE_PRICE_CURRENCY, "KRW")
            .set(ROOM_TYPES.VERSION, 0L)
            .set(ROOM_TYPES.CREATED_AT, now)
            .set(ROOM_TYPES.UPDATED_AT, now)
            .onConflictDoNothing()
            .execute()
    }

    private fun insertGuest(guestId: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(GUESTS)
            .set(GUESTS.ID, guestId)
            .set(GUESTS.PROPERTY_ID, "prop-1")
            .set(GUESTS.NAME, guestId)
            .set(GUESTS.PHONE, "010-0000-0000")
            .set(GUESTS.EMAIL, "$guestId@stayops.com")
            .set(GUESTS.TIER, "NEW")
            .set(GUESTS.TOTAL_VISITS, 0)
            .set(GUESTS.TOTAL_SPEND_AMOUNT, 0L)
            .set(GUESTS.AVERAGE_STAY_NIGHTS, 0.0)
            .set(GUESTS.VERSION, 0L)
            .set(GUESTS.CREATED_AT, now)
            .set(GUESTS.UPDATED_AT, now)
            .onConflictDoNothing()
            .execute()
    }
}
