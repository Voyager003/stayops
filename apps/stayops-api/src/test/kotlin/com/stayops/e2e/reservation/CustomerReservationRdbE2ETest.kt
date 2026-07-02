package com.stayops.e2e.reservation

import com.ninjasquad.springmockk.MockkBean
import com.stayops.RdbTestcontainersConfiguration
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.jooq.generated.Tables.CHANNELS
import com.stayops.jooq.generated.Tables.CHANNEL_MAPPINGS
import com.stayops.jooq.generated.Tables.CHANNEL_MAPPING_ENTRIES
import com.stayops.jooq.generated.Tables.GUESTS
import com.stayops.jooq.generated.Tables.INVENTORY_HOLDS
import com.stayops.jooq.generated.Tables.INVENTORY_HOLD_DATES
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PAYMENTS
import com.stayops.jooq.generated.Tables.PAYMENT_OUTBOX_MESSAGES
import com.stayops.jooq.generated.Tables.PROCESSED_PAYMENT_WEBHOOK_EVENTS
import com.stayops.jooq.generated.Tables.PROCESSED_WEBHOOK_EVENTS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.RATE_PLANS
import com.stayops.jooq.generated.Tables.RATE_PLAN_DAY_OF_WEEK_RULES
import com.stayops.jooq.generated.Tables.RESERVATIONS
import com.stayops.jooq.generated.Tables.RESERVATION_INTENTS
import com.stayops.jooq.generated.Tables.ROOMS
import com.stayops.jooq.generated.Tables.ROOM_INVENTORIES
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.jooq.generated.Tables.ROOM_TYPE_AMENITIES
import com.stayops.jooq.generated.Tables.SCHEDULER_LOCKS
import com.stayops.jooq.generated.Tables.SYNC_TASKS
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.payment.application.required.PaymentConfirmResult
import com.stayops.payment.application.required.PaymentGateway
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.reservation.application.service.ReservationApplication
import com.stayops.reservation.application.service.ReservationIntentExpirationApplication
import com.stayops.reservation.application.service.ReservationPaymentOutboxApplication
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.MutableClock
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("rdb")
@Import(RdbTestcontainersConfiguration::class, FixedTestClockConfig::class)
class CustomerReservationRdbE2ETest @Autowired constructor(
    private val customerReservationApplication: CustomerReservationApplication,
    private val reservationApplication: ReservationApplication,
    private val expirationApplication: ReservationIntentExpirationApplication,
    private val reservationPaymentOutboxApplication: ReservationPaymentOutboxApplication,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val memberRepository: MemberRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationIntentRepository: ReservationIntentRepository,
    private val inventoryHoldRepository: InventoryHoldRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentOutboxRepository: PaymentOutboxRepository,
    private val dsl: DSLContext,
    @Qualifier("fixedTestClock")
    private val clock: Clock,
    @MockkBean private val paymentGateway: PaymentGateway
) {

    private lateinit var checkIn: LocalDate
    private lateinit var checkOut: LocalDate

    @BeforeEach
    fun setUp() {
        (clock as MutableClock).set(FixedTestClockConfig.DEFAULT_INSTANT)
        checkIn = LocalDate.now(clock).plusDays(7)
        checkOut = checkIn.plusDays(1)
        clearRdb()
        createBookableProperty(totalRooms = 1)
    }

    @Nested
    inner class `결제 완료 후 PMS 확정 대기` {

        @Test
        fun `PG 승인이 완료되어도 예약은 PENDING으로 남고 PMS 확정 후 CONFIRMED가 된다`() {
            val intentResult = createIntent()
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_confirm_waiting",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = intentResult.payment.amount.amount
            )

            customerReservationApplication.confirmReservationIntentPayment(
                memberId = CUSTOMER_ID,
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_confirm_waiting",
                orderId = intentResult.payment.orderId,
                amount = intentResult.payment.amount.amount
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-e2e-worker")

            val reservedIntent = reservationIntentRepository.findById(intentResult.intent.id)!!
            val reservation = reservationRepository.findById(reservedIntent.reservationId!!)!!
            val approvedPayment = paymentRepository.findByReservationIntentId(intentResult.intent.id)!!

            assertThat(reservedIntent.status).isEqualTo(ReservationIntentStatus.RESERVED)
            assertThat(reservation.status).isEqualTo(ReservationStatus.PENDING)
            assertThat(approvedPayment.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(approvedPayment.reservationId).isEqualTo(reservation.id)
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.CONSUMED)
            assertThat(availability().reservedCount).isEqualTo(1)

            val confirmed = reservationApplication.confirmReservation(PROPERTY_ID, reservation.id)

            assertThat(confirmed.status).isEqualTo(ReservationStatus.CONFIRMED)
        }
    }

    @Nested
    inner class `결제 승인 보상 처리` {

        @Test
        fun `PG 승인 결과 금액이 요청 금액과 다르면 최종 예약을 만들지 않고 intent와 hold를 실패 보상 처리한다`() {
            val intentResult = createIntent()
            val holdBeforeConfirm = inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!
            assertThat(holdBeforeConfirm.status).isEqualTo(InventoryHoldStatus.HELD)
            assertThat(availability().availableCount).isEqualTo(0)
            assertThat(availability().heldCount).isEqualTo(1)

            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_amount_mismatch",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = BigDecimal("999999.00")
            )

            customerReservationApplication.confirmReservationIntentPayment(
                memberId = CUSTOMER_ID,
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_amount_mismatch",
                orderId = intentResult.payment.orderId,
                amount = intentResult.payment.amount.amount
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-e2e-worker")

            val failedIntent = reservationIntentRepository.findById(intentResult.intent.id)!!
            val failedPayment = paymentRepository.findByReservationIntentId(intentResult.intent.id)!!
            val releasedHold = inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!
            val outbox = paymentOutboxRepository.findByPaymentIdAndType(
                intentResult.payment.id,
                com.stayops.payment.domain.model.PaymentOutboxType.CONFIRM_PAYMENT
            )

            assertThat(failedIntent.status).isEqualTo(ReservationIntentStatus.PAYMENT_FAILED)
            assertThat(failedIntent.reservationId).isNull()
            assertThat(failedPayment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(releasedHold.status).isEqualTo(InventoryHoldStatus.RELEASED)
            assertThat(outbox!!.status).isEqualTo(PaymentOutboxStatus.SKIPPED)
            assertThat(reservationRepository.findPageByMemberId(CUSTOMER_ID, 0, 20).content).isEmpty()
            assertThat(availability().availableCount).isEqualTo(1)
            assertThat(availability().heldCount).isEqualTo(0)
            assertThat(availability().reservedCount).isEqualTo(0)
        }
    }

    @Nested
    inner class `예약 intent 만료` {

        @Test
        fun `만료된 PAYMENT_WAITING intent만 EXPIRED 처리하고 hold를 해제해 같은 날짜 재시도를 허용한다`() {
            val expiredTarget = createIntent(guestPhone = "010-1111-0001")
            customerReservationApplication.createReservationIntent(
                memberId = SECOND_CUSTOMER_ID,
                propertyId = PROPERTY_ID,
                roomTypeId = SECOND_ROOM_TYPE_ID,
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "다른고객",
                guestPhone = "010-1111-0002",
                guestEmail = "second@test.com"
            )

            (clock as MutableClock).advance(Duration.ofMinutes(16))
            val expiredCount = expirationApplication.expirePaymentWaitingIntents()

            assertThat(expiredCount).isEqualTo(2)
            assertThat(reservationIntentRepository.findById(expiredTarget.intent.id)!!.status)
                .isEqualTo(ReservationIntentStatus.EXPIRED)
            assertThat(inventoryHoldRepository.findByReservationIntentId(expiredTarget.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.RELEASED)
            assertThat(availability().availableCount).isEqualTo(1)
            assertThat(availability().heldCount).isEqualTo(0)

            val retry = createIntent(guestPhone = "010-1111-0003")
            assertThat(retry.intent.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)
            assertThat(availability().availableCount).isEqualTo(0)
            assertThat(availability().heldCount).isEqualTo(1)
        }
    }

    @Nested
    inner class `재고 동시성` {

        @Test
        fun `객실이 하나뿐인 날짜에 두 사용자가 동시에 intent를 만들면 하나만 성공하고 재고 수량은 초과되지 않는다`() {
            val executor = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            val successes = mutableListOf<String>()
            val failures = mutableListOf<Throwable>()

            listOf(CUSTOMER_ID, SECOND_CUSTOMER_ID).forEachIndexed { index, memberId ->
                executor.submit {
                    try {
                        ready.countDown()
                        start.await(5, TimeUnit.SECONDS)
                        val result = createIntent(
                            memberId = memberId,
                            guestName = "동시고객$index",
                            guestPhone = "010-2222-000$index",
                            guestEmail = "concurrent$index@test.com"
                        )
                        synchronized(successes) {
                            successes += result.intent.id
                        }
                    } catch (e: Throwable) {
                        synchronized(failures) {
                            failures += e
                        }
                    } finally {
                        done.countDown()
                    }
                }
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue()
            executor.shutdownNow()

            assertThat(successes).hasSize(1)
            assertThat(failures).hasSize(1)
            assertThat(availability().availableCount).isEqualTo(0)
            assertThat(availability().heldCount).isEqualTo(1)
            assertThat(availability().reservedCount).isEqualTo(0)
            assertThat(inventoryHoldRepository.findByReservationIntentId(successes.single())!!.status)
                .isEqualTo(InventoryHoldStatus.HELD)
        }
    }

    private fun createIntent(
        memberId: String = CUSTOMER_ID,
        guestName: String = "RDB고객",
        guestPhone: String = "010-9999-9999",
        guestEmail: String? = "rdb@test.com"
    ) = customerReservationApplication.createReservationIntent(
        memberId = memberId,
        propertyId = PROPERTY_ID,
        roomTypeId = ROOM_TYPE_ID,
        checkIn = checkIn,
        checkOut = checkOut,
        numberOfGuests = 2,
        guestName = guestName,
        guestPhone = guestPhone,
        guestEmail = guestEmail
    )

    private fun availability() =
        inventoryApplication.getAvailability(PROPERTY_ID, ROOM_TYPE_ID, checkIn, checkIn).single()

    private fun createBookableProperty(totalRooms: Int) {
        memberRepository.save(
            Member.create(
                id = OWNER_ID,
                email = "owner-rdb-e2e@test.com",
                passwordHash = "hashed",
                name = "RDB오너",
                role = MemberRole.OWNER
            )
        )
        memberRepository.save(
            Member.create(
                id = CUSTOMER_ID,
                email = "customer-rdb-e2e@test.com",
                passwordHash = "hashed",
                name = "RDB고객",
                role = MemberRole.CUSTOMER
            )
        )
        memberRepository.save(
            Member.create(
                id = SECOND_CUSTOMER_ID,
                email = "second-rdb-e2e@test.com",
                passwordHash = "hashed",
                name = "RDB고객2",
                role = MemberRole.CUSTOMER
            )
        )
        propertyRepository.save(
            Property.create(
                id = PROPERTY_ID,
                ownerId = OWNER_ID,
                name = "RDB E2E 호텔",
                type = PropertyType.HOTEL,
                address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
                contactInfo = ContactInfo.of("02-1234-5678", "rdb-e2e@test.com"),
                description = "RDB E2E 테스트 호텔",
                timezone = "Asia/Seoul",
                currency = "KRW"
            ).activate()
        )
        roomTypeRepository.save(
            RoomType.create(
                id = ROOM_TYPE_ID,
                propertyId = PROPERTY_ID,
                name = "스탠다드룸",
                description = "기본 객실",
                maxOccupancy = 2,
                basePrice = Money.won(100_000)
            )
        )
        roomTypeRepository.save(
            RoomType.create(
                id = SECOND_ROOM_TYPE_ID,
                propertyId = PROPERTY_ID,
                name = "트윈룸",
                description = "만료 격리 객실",
                maxOccupancy = 2,
                basePrice = Money.won(100_000)
            )
        )
        channelRepository.save(Channel.createDirect(id = CHANNEL_ID, propertyId = PROPERTY_ID))

        repeat(totalRooms) { index ->
            roomRepository.save(Room.create("room-rdb-e2e-$index", PROPERTY_ID, ROOM_TYPE_ID, "10$index", 1))
        }
        roomRepository.save(Room.create("room-rdb-e2e-second", PROPERTY_ID, SECOND_ROOM_TYPE_ID, "201", 2))
        inventoryApplication.syncInventoryForRoomType(PROPERTY_ID, ROOM_TYPE_ID)
        inventoryApplication.syncInventoryForRoomType(PROPERTY_ID, SECOND_ROOM_TYPE_ID)
        inventoryApplication.bulkBlock(
            propertyId = PROPERTY_ID,
            roomTypeId = ROOM_TYPE_ID,
            startDate = checkIn,
            endDate = checkOut.minusDays(1),
            daysOfWeek = null,
            action = "UNBLOCK",
            count = totalRooms
        )
        inventoryApplication.bulkBlock(
            propertyId = PROPERTY_ID,
            roomTypeId = SECOND_ROOM_TYPE_ID,
            startDate = checkIn,
            endDate = checkOut.minusDays(1),
            daysOfWeek = null,
            action = "UNBLOCK",
            count = 1
        )
    }

    private fun clearRdb() {
        dsl.deleteFrom(SCHEDULER_LOCKS).execute()
        dsl.deleteFrom(SYNC_TASKS).execute()
        dsl.deleteFrom(PROCESSED_WEBHOOK_EVENTS).execute()
        dsl.deleteFrom(PROCESSED_PAYMENT_WEBHOOK_EVENTS).execute()
        dsl.deleteFrom(PAYMENT_OUTBOX_MESSAGES).execute()
        dsl.deleteFrom(PAYMENTS).execute()
        dsl.deleteFrom(INVENTORY_HOLD_DATES).execute()
        dsl.deleteFrom(INVENTORY_HOLDS).execute()
        dsl.deleteFrom(RESERVATION_INTENTS).execute()
        dsl.deleteFrom(RESERVATIONS).execute()
        dsl.deleteFrom(GUESTS).execute()
        dsl.deleteFrom(ROOM_INVENTORIES).execute()
        dsl.deleteFrom(ROOMS).execute()
        dsl.deleteFrom(ROOM_TYPE_AMENITIES).execute()
        dsl.deleteFrom(RATE_PLAN_DAY_OF_WEEK_RULES).execute()
        dsl.deleteFrom(RATE_PLANS).execute()
        dsl.deleteFrom(CHANNEL_MAPPING_ENTRIES).execute()
        dsl.deleteFrom(CHANNEL_MAPPINGS).execute()
        dsl.deleteFrom(CHANNELS).execute()
        dsl.deleteFrom(ROOM_TYPES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
    }

    companion object {
        private const val OWNER_ID = "owner-rdb-e2e"
        private const val CUSTOMER_ID = "customer-rdb-e2e"
        private const val SECOND_CUSTOMER_ID = "second-customer-rdb-e2e"
        private const val PROPERTY_ID = "prop-rdb-e2e"
        private const val ROOM_TYPE_ID = "rt-rdb-e2e"
        private const val SECOND_ROOM_TYPE_ID = "rt-rdb-e2e-second"
        private const val CHANNEL_ID = "ch-rdb-e2e"
    }
}
