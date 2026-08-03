package com.stayops.e2e.reservation

import com.ninjasquad.springmockk.MockkBean
import com.stayops.RdbTestcontainersConfiguration
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.provided.InventoryHoldService
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.jooq.generated.Tables.ACTIVE_RESERVATION_ATTEMPTS
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
import com.stayops.jooq.generated.Tables.RESERVATION_INTENT_REQUESTS
import com.stayops.jooq.generated.Tables.ROOMS
import com.stayops.jooq.generated.Tables.ROOM_INVENTORIES
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.jooq.generated.Tables.ROOM_TYPE_AMENITIES
import com.stayops.jooq.generated.Tables.SCHEDULER_LOCKS
import com.stayops.jooq.generated.Tables.SYNC_TASKS
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.payment.application.required.PaymentCancelResult
import com.stayops.payment.application.required.PaymentConfirmResult
import com.stayops.payment.application.required.PaymentGateway
import com.stayops.payment.application.required.PaymentGatewayException
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.reservation.application.service.CustomerReservationIntentResult
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
import com.stayops.shared.exception.ConflictException
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    private val inventoryHoldService: InventoryHoldService,
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
    inner class `예약 intent 생성 멱등성` {
        @Test
        fun `같은 키로 재요청하면 기존 intent를 반환하고 재고를 한 번만 점유한다`() {
            val first = createIntent(idempotencyKey = "same-intent-key")

            val replayed = createIntent(idempotencyKey = "same-intent-key")

            assertThat(replayed.intent.id).isEqualTo(first.intent.id)
            assertThat(replayed.payment.id).isEqualTo(first.payment.id)
            assertThat(availability().heldCount).isEqualTo(1)
            assertThat(paymentRepository.findByMemberId(CUSTOMER_ID)).hasSize(1)
            assertThat(dsl.fetchCount(RESERVATION_INTENT_REQUESTS)).isEqualTo(1)
        }

        @Test
        fun `같은 키의 동시 요청도 하나의 intent와 payment를 반환한다`() {
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val futures = (1..2).map {
                    executor.submit<CustomerReservationIntentResult> {
                        start.await()
                        createIntent(idempotencyKey = "concurrent-intent-key")
                    }
                }
                start.countDown()

                val results = futures.map { it.get(10, TimeUnit.SECONDS) }

                assertThat(results.map { it.intent.id }.distinct()).hasSize(1)
                assertThat(results.map { it.payment.id }.distinct()).hasSize(1)
                assertThat(dsl.fetchCount(RESERVATION_INTENTS)).isEqualTo(1)
                assertThat(dsl.fetchCount(RESERVATION_INTENT_REQUESTS)).isEqualTo(1)
                assertThat(paymentRepository.findByMemberId(CUSTOMER_ID)).hasSize(1)
                assertThat(availability().heldCount).isEqualTo(1)
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `서로 다른 키라도 동일 회원의 활성 예약 시도는 하나만 허용한다`() {
            clearRdb()
            createBookableProperty(totalRooms = 2)

            createIntent(idempotencyKey = "booking-attempt-key-1")

            assertThatThrownBy {
                createIntent(idempotencyKey = "booking-attempt-key-2")
            }.isInstanceOf(ConflictException::class.java)
                .extracting("code")
                .isEqualTo("DUPLICATE_ACTIVE_RESERVATION_ATTEMPT")
            assertThat(dsl.fetchCount(RESERVATION_INTENTS)).isEqualTo(1)
            assertThat(dsl.fetchCount(RESERVATION_INTENT_REQUESTS)).isEqualTo(1)
            assertThat(dsl.fetchCount(ACTIVE_RESERVATION_ATTEMPTS)).isEqualTo(1)
            assertThat(paymentRepository.findByMemberId(CUSTOMER_ID)).hasSize(1)
            assertThat(availability().heldCount).isEqualTo(1)
        }

        @Test
        fun `서로 다른 키의 동일 회원 동시 요청도 하나만 커밋한다`() {
            clearRdb()
            createBookableProperty(totalRooms = 2)
            val executor = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val successes = mutableListOf<CustomerReservationIntentResult>()
            val failures = mutableListOf<Throwable>()
            try {
                (1..2).forEach { index ->
                    executor.submit {
                        try {
                            ready.countDown()
                            start.await(5, TimeUnit.SECONDS)
                            val result = createIntent(idempotencyKey = "concurrent-booking-key-$index")
                            synchronized(successes) { successes += result }
                        } catch (exception: Throwable) {
                            synchronized(failures) { failures += exception }
                        }
                    }
                }
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
                start.countDown()
                executor.shutdown()
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue()

                assertThat(successes).hasSize(1)
                assertThat(failures).hasSize(1)
                assertThat(failures.single()).isInstanceOf(ConflictException::class.java)
                assertThat((failures.single() as ConflictException).code)
                    .isEqualTo("DUPLICATE_ACTIVE_RESERVATION_ATTEMPT")
                assertThat(dsl.fetchCount(RESERVATION_INTENTS)).isEqualTo(1)
                assertThat(dsl.fetchCount(RESERVATION_INTENT_REQUESTS)).isEqualTo(1)
                assertThat(dsl.fetchCount(ACTIVE_RESERVATION_ATTEMPTS)).isEqualTo(1)
                assertThat(paymentRepository.findByMemberId(CUSTOMER_ID)).hasSize(1)
                assertThat(availability().heldCount).isEqualTo(1)
            } finally {
                executor.shutdownNow()
            }
        }
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
            assertThat(dsl.fetchCount(ACTIVE_RESERVATION_ATTEMPTS)).isZero()

            val confirmed = reservationApplication.confirmReservation(PROPERTY_ID, reservation.id)

            assertThat(confirmed.status).isEqualTo(ReservationStatus.CONFIRMED)
        }
    }

    @Nested
    inner class `결제 승인 보상 처리` {

        @Test
        fun `PG 승인 후 hold 소비에 실패하면 TX3의 내부 저장을 모두 롤백한다`() {
            val intentResult = createIntent()
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_tx3_rollback",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = intentResult.payment.amount.amount
            )

            customerReservationApplication.confirmReservationIntentPayment(
                memberId = CUSTOMER_ID,
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_tx3_rollback",
                orderId = intentResult.payment.orderId,
                amount = intentResult.payment.amount.amount
            )
            inventoryHoldService.release(intentResult.intent.id)

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-tx3-rollback-worker")

            val intent = reservationIntentRepository.findById(intentResult.intent.id)!!
            val payment = paymentRepository.findByReservationIntentId(intentResult.intent.id)!!
            val outbox = paymentOutboxRepository.findByPaymentIdAndType(
                intentResult.payment.id,
                PaymentOutboxType.CONFIRM_PAYMENT
            )!!

            assertThat(intent.status).isEqualTo(ReservationIntentStatus.CONFIRM_REQUESTED)
            assertThat(intent.reservationId).isNull()
            assertThat(payment.status).isEqualTo(PaymentStatus.CONFIRM_REQUESTED)
            assertThat(payment.reservationId).isNull()
            assertThat(outbox.status).isEqualTo(PaymentOutboxStatus.PENDING)
            assertThat(reservationRepository.findPageByMemberId(CUSTOMER_ID, 0, 20).content).isEmpty()
            assertThat(dsl.fetchCount(GUESTS)).isZero()
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.RELEASED)
        }

        @Test
        fun `hold 소비 후 intent 저장이 충돌하면 TX3의 내부 저장과 hold 소비를 모두 롤백한다`() {
            val intentResult = createIntent(guestPhone = "010-3333-3334")
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = CUSTOMER_ID,
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_rdb_tx3_late_rollback",
                orderId = intentResult.payment.orderId,
                amount = intentResult.payment.amount.amount
            )
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.HELD)
            every { paymentGateway.confirm(any(), any(), any(), any()) } answers {
                val concurrentIntent = reservationIntentRepository.findById(intentResult.intent.id)!!
                reservationIntentRepository.save(
                    concurrentIntent.failPayment("동시 상태 변경", Instant.now(clock))
                )
                PaymentConfirmResult(
                    paymentKey = "toss_pk_rdb_tx3_late_rollback",
                    orderId = intentResult.payment.orderId,
                    method = "카드",
                    approvedAt = Instant.now(clock),
                    totalAmount = intentResult.payment.amount.amount
                )
            }

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-tx3-late-rollback-worker")

            val intent = reservationIntentRepository.findById(intentResult.intent.id)!!
            val payment = paymentRepository.findByReservationIntentId(intentResult.intent.id)!!
            val outbox = paymentOutboxRepository.findByPaymentIdAndType(
                intentResult.payment.id,
                PaymentOutboxType.CONFIRM_PAYMENT
            )!!

            assertThat(intent.status).isEqualTo(ReservationIntentStatus.PAYMENT_FAILED)
            assertThat(intent.reservationId).isNull()
            assertThat(payment.status).isEqualTo(PaymentStatus.CONFIRM_REQUESTED)
            assertThat(payment.reservationId).isNull()
            assertThat(outbox.status).isEqualTo(PaymentOutboxStatus.PENDING)
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.HELD)
            assertThat(reservationRepository.findPageByMemberId(CUSTOMER_ID, 0, 20).content).isEmpty()
            assertThat(dsl.fetchCount(GUESTS)).isZero()
        }

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
            assertThat(dsl.fetchCount(ACTIVE_RESERVATION_ATTEMPTS)).isZero()

            val retry = createIntent(
                idempotencyKey = "retry-after-payment-failure-key",
                guestPhone = "010-9999-0002"
            )
            assertThat(retry.intent.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)
            assertThat(dsl.fetchCount(ACTIVE_RESERVATION_ATTEMPTS)).isEqualTo(1)
        }

        @Test
        fun `내부 예약 확정이 3회 실패하면 결제 취소를 요청하고 PG 취소 성공 후 완료한다`() {
            val mutableClock = clock as MutableClock
            val intentResult = createIntent(guestPhone = "010-4444-4444")
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_rdb_compensation",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = intentResult.payment.amount.amount
            )
            every { paymentGateway.cancel(any(), any(), any()) } throws
                PaymentGatewayException.ProviderError("PROVIDER_ERROR", "PG사 취소 일시 장애")
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = CUSTOMER_ID,
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_rdb_compensation",
                orderId = intentResult.payment.orderId,
                amount = intentResult.payment.amount.amount
            )
            inventoryHoldService.release(intentResult.intent.id)

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-compensation-worker-1")
            mutableClock.advance(Duration.ofSeconds(31))
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-compensation-worker-2")
            mutableClock.advance(Duration.ofSeconds(61))
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-compensation-worker-3")

            val confirmOutbox = paymentOutboxRepository.findByPaymentIdAndType(
                intentResult.payment.id,
                PaymentOutboxType.CONFIRM_PAYMENT
            )!!
            val cancelOutbox = paymentOutboxRepository.findByPaymentIdAndType(
                intentResult.payment.id,
                PaymentOutboxType.CANCEL_PAYMENT
            )!!
            val cancelRequestedPayment = paymentRepository.findByReservationIntentId(intentResult.intent.id)!!
            val failedIntent = reservationIntentRepository.findById(intentResult.intent.id)!!

            assertThat(confirmOutbox.status).isEqualTo(PaymentOutboxStatus.FAILED)
            assertThat(confirmOutbox.retryCount).isEqualTo(3)
            assertThat(cancelOutbox.status).isEqualTo(PaymentOutboxStatus.PENDING)
            assertThat(cancelOutbox.retryCount).isEqualTo(1)
            assertThat(cancelRequestedPayment.status).isEqualTo(PaymentStatus.CANCEL_REQUESTED)
            assertThat(failedIntent.status).isEqualTo(ReservationIntentStatus.PAYMENT_FAILED)
            assertThat(failedIntent.reservationId).isNull()
            assertThat(reservationRepository.findPageByMemberId(CUSTOMER_ID, 0, 20).content).isEmpty()
            assertThat(dsl.fetchCount(GUESTS)).isZero()
            assertThat(dsl.fetchCount(ACTIVE_RESERVATION_ATTEMPTS)).isZero()

            every { paymentGateway.cancel(any(), any(), any()) } returns
                PaymentCancelResult("toss_pk_rdb_compensation")
            mutableClock.advance(Duration.ofSeconds(31))

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-compensation-worker-4")

            assertThat(paymentRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(PaymentStatus.CANCELLED)
            assertThat(paymentOutboxRepository.findById(cancelOutbox.id)!!.status)
                .isEqualTo(PaymentOutboxStatus.COMPLETED)
            verify(exactly = 3) { paymentGateway.confirm(any(), any(), any(), any()) }
            verify(exactly = 2) { paymentGateway.cancel(any(), any(), any()) }
        }
    }

    @Nested
    inner class `결제 승인 멱등성` {

        @Test
        fun `완료된 confirm Outbox가 재전달되어도 PG와 예약을 중복 처리하지 않는다`() {
            val intentResult = createIntent(guestPhone = "010-7777-7777")
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_rdb_worker_idempotent",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = intentResult.payment.amount.amount
            )
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = CUSTOMER_ID,
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_rdb_worker_idempotent",
                orderId = intentResult.payment.orderId,
                amount = intentResult.payment.amount.amount
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-idempotency-worker-1")

            val firstReservationId = reservationIntentRepository.findById(intentResult.intent.id)!!.reservationId!!
            val outbox = paymentOutboxRepository.findByPaymentIdAndType(
                intentResult.payment.id,
                PaymentOutboxType.CONFIRM_PAYMENT
            )!!
            dsl.update(PAYMENT_OUTBOX_MESSAGES)
                .set(PAYMENT_OUTBOX_MESSAGES.STATUS, PaymentOutboxStatus.PENDING.name)
                .setNull(PAYMENT_OUTBOX_MESSAGES.NEXT_RETRY_AT)
                .setNull(PAYMENT_OUTBOX_MESSAGES.LOCKED_BY)
                .setNull(PAYMENT_OUTBOX_MESSAGES.LOCKED_UNTIL)
                .where(PAYMENT_OUTBOX_MESSAGES.ID.eq(outbox.id))
                .execute()

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-idempotency-worker-2")

            val replayedIntent = reservationIntentRepository.findById(intentResult.intent.id)!!
            val reservations = reservationRepository.findPageByMemberId(CUSTOMER_ID, 0, 20).content
            assertThat(replayedIntent.reservationId).isEqualTo(firstReservationId)
            assertThat(reservations).hasSize(1)
            assertThat(reservations.single().id).isEqualTo(firstReservationId)
            assertThat(paymentOutboxRepository.findById(outbox.id)!!.status)
                .isEqualTo(PaymentOutboxStatus.COMPLETED)
            verify(exactly = 1) { paymentGateway.confirm(any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `예약 intent 만료` {

        @Test
        fun `만료된 PAYMENT_WAITING intent만 EXPIRED 처리하고 hold를 해제해 같은 날짜 재시도를 허용한다`() {
            val expiredTarget = createIntent(guestPhone = "010-1111-0001")
            customerReservationApplication.createReservationIntent(
                idempotencyKey = "test-intent-key",
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

            val retry = createIntent(
                idempotencyKey = "retry-after-expiration-key",
                guestPhone = "010-1111-0003"
            )
            assertThat(retry.intent.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)
            assertThat(availability().availableCount).isEqualTo(0)
            assertThat(availability().heldCount).isEqualTo(1)
        }
    }

    @Nested
    inner class `재고 동시성` {

        @Test
        fun `객실이 하나뿐인 날짜에 두 사용자가 동시에 결제하면 한 명만 hold와 최종 예약에 성공한다`() {
            val executor = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            val successes = mutableListOf<Pair<String, String>>()
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
                            successes += memberId to result.intent.id
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
            val (winnerMemberId, winnerIntentId) = successes.single()
            assertThat(inventoryHoldRepository.findByReservationIntentId(winnerIntentId)!!.status)
                .isEqualTo(InventoryHoldStatus.HELD)

            val winnerPayment = paymentRepository.findByReservationIntentId(winnerIntentId)!!
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_rdb_concurrent",
                orderId = winnerPayment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = winnerPayment.amount.amount
            )
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = winnerMemberId,
                reservationIntentId = winnerIntentId,
                paymentKey = "toss_pk_rdb_concurrent",
                orderId = winnerPayment.orderId,
                amount = winnerPayment.amount.amount
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "rdb-concurrent-worker")

            val reservedIntent = reservationIntentRepository.findById(winnerIntentId)!!
            val reservations = reservationRepository.findPageByMemberId(winnerMemberId, 0, 20).content
            assertThat(reservedIntent.status).isEqualTo(ReservationIntentStatus.RESERVED)
            assertThat(reservations).hasSize(1)
            assertThat(reservations.single().id).isEqualTo(reservedIntent.reservationId)
            assertThat(paymentRepository.findByReservationIntentId(winnerIntentId)!!.status)
                .isEqualTo(PaymentStatus.APPROVED)
            assertThat(inventoryHoldRepository.findByReservationIntentId(winnerIntentId)!!.status)
                .isEqualTo(InventoryHoldStatus.CONSUMED)
            assertThat(availability().availableCount).isEqualTo(0)
            assertThat(availability().heldCount).isEqualTo(0)
            assertThat(availability().reservedCount).isEqualTo(1)
        }
    }

    private fun createIntent(
        idempotencyKey: String = "test-intent-key",
        memberId: String = CUSTOMER_ID,
        guestName: String = "RDB고객",
        guestPhone: String = "010-9999-9999",
        guestEmail: String? = "rdb@test.com"
    ) = customerReservationApplication.createReservationIntent(
        idempotencyKey = idempotencyKey,
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
        dsl.deleteFrom(ACTIVE_RESERVATION_ATTEMPTS).execute()
        dsl.deleteFrom(RESERVATION_INTENT_REQUESTS).execute()
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
