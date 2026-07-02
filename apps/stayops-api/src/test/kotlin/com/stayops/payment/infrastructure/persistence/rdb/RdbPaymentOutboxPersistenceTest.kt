package com.stayops.payment.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.jooq.generated.Tables.GUESTS
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PAYMENTS
import com.stayops.jooq.generated.Tables.PAYMENT_OUTBOX_MESSAGES
import com.stayops.jooq.generated.Tables.PROCESSED_PAYMENT_WEBHOOK_EVENTS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.RESERVATIONS
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import com.stayops.payment.domain.model.PaymentOutboxMessage
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.ProcessedPaymentWebhookEvent
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.ProcessedPaymentWebhookEventRepository
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

@SpringJUnitConfig(classes = [RdbPaymentOutboxPersistenceTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbPaymentOutboxPersistenceTest @Autowired constructor(
    private val paymentOutboxRepository: PaymentOutboxRepository,
    private val processedPaymentWebhookEventRepository: ProcessedPaymentWebhookEventRepository,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(
        RdbTestcontainersConfiguration::class,
        RdbPaymentOutboxRepository::class,
        RdbProcessedPaymentWebhookEventRepository::class
    )
    class TestApplication

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(PROCESSED_PAYMENT_WEBHOOK_EVENTS).execute()
        dsl.deleteFrom(PAYMENT_OUTBOX_MESSAGES).execute()
        dsl.deleteFrom(PAYMENTS).execute()
        dsl.deleteFrom(RESERVATIONS).execute()
        dsl.deleteFrom(GUESTS).execute()
        dsl.deleteFrom(ROOM_TYPES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
    }

    @Nested
    inner class `PaymentOutbox 저장소` {

        @Test
        fun `Outbox 메시지를 저장하고 처리 가능한 목록을 조회한다`() {
            insertPayment()
            val now = Instant.parse("2026-07-01T00:00:00Z")
            val message = PaymentOutboxMessage.createConfirm(
                id = "outbox-1",
                paymentId = "pay-1",
                reservationId = "res-1",
                memberId = "member-1",
                paymentKey = "payment-key-1",
                orderId = "order-1",
                amount = Money.of(220_000),
                now = now
            )

            paymentOutboxRepository.save(message)

            val found = paymentOutboxRepository.findByPaymentIdAndType("pay-1", PaymentOutboxType.CONFIRM_PAYMENT)
            val ready = paymentOutboxRepository.findReadyForProcessing(now)

            assertThat(found!!.id).isEqualTo("outbox-1")
            assertThat(found.amount).isEqualTo(Money.of(BigDecimal("220000.00")))
            assertThat(ready.map { it.id }).containsExactly("outbox-1")
        }
    }

    @Nested
    inner class `ProcessedPaymentWebhookEvent 저장소` {

        @Test
        fun `같은 transmissionId는 한 번만 저장한다`() {
            val event = ProcessedPaymentWebhookEvent(
                id = "event-1",
                transmissionId = "tx-1",
                eventType = "PAYMENT_STATUS_CHANGED",
                paymentKey = "payment-key-1",
                orderId = "order-1",
                processedAt = Instant.parse("2026-07-01T00:00:00Z")
            )

            assertThat(processedPaymentWebhookEventRepository.saveIfAbsent(event)).isTrue()
            assertThat(processedPaymentWebhookEventRepository.existsByTransmissionId("tx-1")).isTrue()
            assertThat(processedPaymentWebhookEventRepository.saveIfAbsent(event.copy(id = "event-2"))).isFalse()
        }
    }

    private fun insertPayment() {
        insertReservation()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dsl.insertInto(PAYMENTS)
            .set(PAYMENTS.ID, "pay-1")
            .set(PAYMENTS.RESERVATION_ID, "res-1")
            .set(PAYMENTS.MEMBER_ID, "member-1")
            .set(PAYMENTS.ORDER_ID, "order-1")
            .set(PAYMENTS.AMOUNT, BigDecimal("220000.00"))
            .set(PAYMENTS.CURRENCY, "KRW")
            .set(PAYMENTS.STATUS, "APPROVED")
            .set(PAYMENTS.PAYMENT_KEY, "payment-key-1")
            .set(PAYMENTS.METHOD, "CARD")
            .set(PAYMENTS.VERSION, 0L)
            .set(PAYMENTS.CREATED_AT, now)
            .set(PAYMENTS.UPDATED_AT, now)
            .execute()
    }

    private fun insertReservation() {
        insertMember("member-1")
        insertProperty("prop-1")
        insertRoomType("rt-1")
        insertGuest("guest-1")
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dsl.insertInto(RESERVATIONS)
            .set(RESERVATIONS.ID, "res-1")
            .set(RESERVATIONS.PROPERTY_ID, "prop-1")
            .set(RESERVATIONS.ROOM_TYPE_ID, "rt-1")
            .set(RESERVATIONS.GUEST_ID, "guest-1")
            .set(RESERVATIONS.GUEST_NAME, "김고객")
            .set(RESERVATIONS.GUEST_PHONE, "010-0000-0000")
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
            .execute()
    }

    private fun insertGuest(guestId: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(GUESTS)
            .set(GUESTS.ID, guestId)
            .set(GUESTS.PROPERTY_ID, "prop-1")
            .set(GUESTS.NAME, guestId)
            .set(GUESTS.PHONE, "010-0000-0000")
            .set(GUESTS.TIER, "NEW")
            .set(GUESTS.TOTAL_VISITS, 0)
            .set(GUESTS.TOTAL_SPEND_AMOUNT, 0L)
            .set(GUESTS.AVERAGE_STAY_NIGHTS, 0.0)
            .set(GUESTS.VERSION, 0L)
            .set(GUESTS.CREATED_AT, now)
            .set(GUESTS.UPDATED_AT, now)
            .execute()
    }
}
