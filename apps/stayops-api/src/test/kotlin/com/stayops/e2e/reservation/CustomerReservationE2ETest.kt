package com.stayops.e2e.reservation

import com.stayops.TestcontainersConfiguration
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.reservation.application.service.CustomerReservationIntentResult
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.provided.InventoryHoldService
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.application.service.ReservationApplication
import com.stayops.reservation.application.service.ReservationPaymentOutboxApplication
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.application.required.PaymentCancelResult
import com.stayops.payment.application.required.PaymentConfirmResult
import com.stayops.payment.application.required.PaymentGateway
import com.stayops.payment.application.required.PaymentGatewayException
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
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
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class CustomerReservationE2ETest @Autowired constructor(
    private val customerReservationApplication: CustomerReservationApplication,
    private val reservationApplication: ReservationApplication,
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
    private val reservationPaymentOutboxApplication: ReservationPaymentOutboxApplication,
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock,
    @MockkBean private val paymentGateway: PaymentGateway
) {

    private lateinit var checkIn: LocalDate
    private lateinit var checkOut: LocalDate

    @BeforeEach
    fun setUp() {
        (clock as MutableClock).set(FixedTestClockConfig.DEFAULT_INSTANT)
        checkIn = LocalDate.now(clock).plusDays(7)
        checkOut = checkIn.plusDays(2)

        // Clean all documents (preserve indexes)
        mongoTemplate.collectionNames.forEach { name ->
            mongoTemplate.getCollection(name).deleteMany(org.bson.Document())
        }

        // Setup test data
        val property = propertyRepository.save(
            Property.create(
                id = "prop-e2e", ownerId = "owner-1", name = "E2E 호텔",
                type = PropertyType.HOTEL,
                address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
                contactInfo = ContactInfo.of("02-1234-5678", "e2e@test.com"),
                description = "E2E 테스트 호텔", timezone = "Asia/Seoul", currency = "KRW"
            ).activate()
        )

        val roomType = roomTypeRepository.save(
            RoomType.create(
                id = "rt-e2e", propertyId = "prop-e2e", name = "스탠다드룸",
                description = "기본 객실", maxOccupancy = 2,
                basePrice = Money.won(100_000)
            )
        )

        channelRepository.save(Channel.createDirect(id = "ch-e2e", propertyId = "prop-e2e"))

        // Room 추가 → 재고 자동 생성
        roomRepository.save(Room.create("room-e2e-1", "prop-e2e", "rt-e2e", "101", 1))
        roomRepository.save(Room.create("room-e2e-2", "prop-e2e", "rt-e2e", "102", 1))
        roomRepository.save(Room.create("room-e2e-3", "prop-e2e", "rt-e2e", "103", 1))
        inventoryApplication.syncInventoryForRoomType("prop-e2e", "rt-e2e")

        // 기본 마감 상태에서 예약 대상 날짜를 오픈
        val processed = inventoryApplication.bulkBlock(
            propertyId = "prop-e2e",
            roomTypeId = "rt-e2e",
            startDate = checkIn,
            endDate = checkOut.minusDays(1),
            daysOfWeek = null,
            action = "UNBLOCK",
            count = 3
        )
        assertThat(processed).isGreaterThan(0)

        memberRepository.save(
            Member.create(
                id = "customer-e2e", email = "e2e@test.com",
                passwordHash = "hashed", name = "E2E고객",
                role = MemberRole.CUSTOMER
            )
        )
    }

    @Nested
    inner class `예약_intent_생성_멱등성` {
        @Test
        fun `같은_키로_재요청하면_기존_intent를_반환하고_재고를_한_번만_점유한다`() {
            val first = customerReservationApplication.createReservationIntent(
                idempotencyKey = "same-intent-key",
                memberId = "customer-e2e",
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "E2E고객",
                guestPhone = "010-9999-9999",
                guestEmail = "e2e@test.com"
            )

            val replayed = customerReservationApplication.createReservationIntent(
                idempotencyKey = "same-intent-key",
                memberId = "customer-e2e",
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "E2E고객",
                guestPhone = "010-9999-9999",
                guestEmail = "e2e@test.com"
            )

            assertThat(replayed.intent.id).isEqualTo(first.intent.id)
            assertThat(replayed.payment.id).isEqualTo(first.payment.id)
            assertThat(
                inventoryApplication.getAvailability("prop-e2e", "rt-e2e", checkIn, checkOut.minusDays(1))
            ).allSatisfy { availability -> assertThat(availability.heldCount).isEqualTo(1) }
            assertThat(paymentRepository.findByMemberId("customer-e2e")).hasSize(1)
        }

        @Test
        fun `같은_키의_동시_요청도_하나의_intent와_payment를_반환한다`() {
            val start = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(2)
            try {
                val futures = (1..2).map {
                    executor.submit<CustomerReservationIntentResult> {
                        start.await()
                        customerReservationApplication.createReservationIntent(
                            idempotencyKey = "concurrent-intent-key",
                            memberId = "customer-e2e",
                            propertyId = "prop-e2e",
                            roomTypeId = "rt-e2e",
                            checkIn = checkIn,
                            checkOut = checkOut,
                            numberOfGuests = 2,
                            guestName = "E2E고객",
                            guestPhone = "010-9999-9999",
                            guestEmail = "e2e@test.com"
                        )
                    }
                }
                start.countDown()

                val results = futures.map { it.get(10, TimeUnit.SECONDS) }

                assertThat(results.map { it.intent.id }.distinct()).hasSize(1)
                assertThat(results.map { it.payment.id }.distinct()).hasSize(1)
                assertThat(mongoTemplate.getCollection("reservation_intents").countDocuments()).isEqualTo(1)
                assertThat(mongoTemplate.getCollection("reservation_intent_requests").countDocuments()).isEqualTo(1)
                assertThat(paymentRepository.findByMemberId("customer-e2e")).hasSize(1)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Nested
    inner class `전체_예매_플로우` {

        @Test
        fun `예약_intent_생성_결제_승인_예약_확정_취소_전체_흐름`() {
            // 1. 예약 intent 생성
            val intentResult = customerReservationApplication.createReservationIntent(
                idempotencyKey = "test-intent-key",
                memberId = "customer-e2e",
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "E2E고객",
                guestPhone = "010-9999-9999",
                guestEmail = "e2e@test.com"
            )

            assertThat(intentResult.intent.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)
            assertThat(intentResult.payment.status).isEqualTo(ReservationPaymentStatus.PENDING)
            assertThat(intentResult.payment.reservationId).isNull()
            assertThat(intentResult.payment.reservationIntentId).isEqualTo(intentResult.intent.id)
            assertThat(reservationRepository.findPageByMemberId("customer-e2e", 0, 20).content).isEmpty()
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.HELD)

            // 2. 결제 확인
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_e2e",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = BigDecimal(200_000)
            )

            val requested = customerReservationApplication.confirmReservationIntentPayment(
                memberId = "customer-e2e",
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_e2e",
                orderId = intentResult.payment.orderId,
                amount = BigDecimal(200_000)
            )
            assertThat(requested.intent.status).isEqualTo(ReservationIntentStatus.CONFIRM_REQUESTED)
            assertThat(requested.payment.status).isEqualTo(ReservationPaymentStatus.CONFIRM_REQUESTED)

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            val reservedIntent = reservationIntentRepository.findById(intentResult.intent.id)!!
            val reservationId = reservedIntent.reservationId!!
            val paymentCompletedReservation = reservationRepository.findById(reservationId)!!
            val confirmedPayment = paymentRepository.findByReservationIntentId(intentResult.intent.id)!!

            assertThat(reservedIntent.status).isEqualTo(ReservationIntentStatus.RESERVED)
            assertThat(paymentCompletedReservation.status).isEqualTo(ReservationStatus.PENDING)
            assertThat(confirmedPayment.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(confirmedPayment.paymentKey).isEqualTo("toss_pk_e2e")
            assertThat(confirmedPayment.reservationId).isEqualTo(reservationId)
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.CONSUMED)

            // 3. 마이페이지 조회
            val myReservations = customerReservationApplication.getMyReservations("customer-e2e")
            assertThat(myReservations.content).hasSize(1)
            assertThat(myReservations.content[0].reservation.id).isEqualTo(reservationId)
            assertThat(myReservations.content[0].reservation.status).isEqualTo(ReservationStatus.PENDING)
            assertThat(myReservations.content[0].payment?.status).isEqualTo(ReservationPaymentStatus.APPROVED)

            // 4. PMS 확정
            val confirmedReservation = reservationApplication.confirmReservation("prop-e2e", reservationId)
            assertThat(confirmedReservation.status).isEqualTo(ReservationStatus.CONFIRMED)

            // 5. 예약 취소 + 환불
            every { paymentGateway.cancel("toss_pk_e2e", any(), any()) } returns PaymentCancelResult("toss_pk_e2e")

            val cancelRequested = customerReservationApplication.cancelReservation(
                memberId = "customer-e2e",
                reservationId = reservationId
            )

            assertThat(cancelRequested.reservation.status).isEqualTo(ReservationStatus.CANCELLED)
            assertThat(cancelRequested.payment.status).isEqualTo(ReservationPaymentStatus.CANCEL_REQUESTED)

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            val cancelledPayment = paymentRepository.findByReservationId(reservationId)!!
            assertThat(cancelledPayment.status).isEqualTo(PaymentStatus.CANCELLED)
        }
    }

    @Nested
    inner class `중복_예약_방지` {

        @Test
        fun `확정된_예약과_동일_조건으로_예약_intent를_생성하면_ConflictException_발생`() {
            val intentResult = customerReservationApplication.createReservationIntent(
                idempotencyKey = "test-intent-key",
                memberId = "customer-e2e",
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "E2E고객",
                guestPhone = "010-9999-9999",
                guestEmail = "e2e@test.com"
            )
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_duplicate",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = BigDecimal(200_000)
            )
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = "customer-e2e",
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_duplicate",
                orderId = intentResult.payment.orderId,
                amount = BigDecimal(200_000)
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            assertThatThrownBy {
                customerReservationApplication.createReservationIntent(
                    idempotencyKey = "new-booking-attempt-key",
                    memberId = "customer-e2e",
                    propertyId = "prop-e2e",
                    roomTypeId = "rt-e2e",
                    checkIn = checkIn,
                    checkOut = checkOut,
                    numberOfGuests = 2,
                    guestName = "E2E고객",
                    guestPhone = "010-9999-9999",
                    guestEmail = "e2e@test.com"
                )
            }.isInstanceOf(ConflictException::class.java)
                .hasMessageContaining("이미 동일 조건의 예약이 존재합니다")
        }
    }

    @Nested
    inner class `결제_확인_멱등성` {

        @Test
        fun `결제가_이미_완료된_예약_intent에_confirmPayment를_다시_호출하면_기존_결과_반환`() {
            val intentResult = customerReservationApplication.createReservationIntent(
                idempotencyKey = "test-intent-key",
                memberId = "customer-e2e",
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "E2E고객",
                guestPhone = "010-7777-7777",
                guestEmail = null
            )

            // 2. 첫 번째 결제 확인
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_idempotent",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = BigDecimal(200_000)
            )

            val firstResult = customerReservationApplication.confirmReservationIntentPayment(
                memberId = "customer-e2e",
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_idempotent",
                orderId = intentResult.payment.orderId,
                amount = BigDecimal(200_000)
            )

            assertThat(firstResult.intent.status).isEqualTo(ReservationIntentStatus.CONFIRM_REQUESTED)
            assertThat(firstResult.payment.status).isEqualTo(ReservationPaymentStatus.CONFIRM_REQUESTED)

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            // 3. 두 번째 결제 확인 — Toss 호출 없이 기존 결과 반환
            val secondResult = customerReservationApplication.confirmReservationIntentPayment(
                memberId = "customer-e2e",
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_idempotent",
                orderId = intentResult.payment.orderId,
                amount = BigDecimal(200_000)
            )

            assertThat(secondResult.intent.status).isEqualTo(ReservationIntentStatus.RESERVED)
            assertThat(secondResult.payment.status).isEqualTo(ReservationPaymentStatus.APPROVED)
        }

        @Test
        fun `완료된_confirm_outbox가_재전달되어도_PG와_예약을_중복_처리하지_않는다`() {
            val intentResult = customerReservationApplication.createReservationIntent(
                idempotencyKey = "test-intent-key",
                memberId = "customer-e2e",
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "멱등고객",
                guestPhone = "010-7777-7778",
                guestEmail = "idempotent@test.com"
            )
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_worker_idempotent",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = intentResult.payment.amount.amount
            )
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = "customer-e2e",
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_worker_idempotent",
                orderId = intentResult.payment.orderId,
                amount = intentResult.payment.amount.amount
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "mongo-idempotency-worker-1")

            val firstReservationId = reservationIntentRepository.findById(intentResult.intent.id)!!.reservationId!!
            val outbox = paymentOutboxRepository.findByPaymentIdAndType(
                intentResult.payment.id,
                PaymentOutboxType.CONFIRM_PAYMENT
            )!!
            mongoTemplate.getCollection("payment_outbox_messages").updateOne(
                org.bson.Document("_id", outbox.id),
                org.bson.Document(
                    "\$set",
                    org.bson.Document("status", PaymentOutboxStatus.PENDING.name)
                        .append("nextRetryAt", null)
                        .append("lockedBy", null)
                        .append("lockedUntil", null)
                )
            )

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "mongo-idempotency-worker-2")

            val replayedIntent = reservationIntentRepository.findById(intentResult.intent.id)!!
            val reservations = reservationRepository.findPageByMemberId("customer-e2e", 0, 20).content
            assertThat(replayedIntent.reservationId).isEqualTo(firstReservationId)
            assertThat(reservations).hasSize(1)
            assertThat(reservations.single().id).isEqualTo(firstReservationId)
            assertThat(paymentOutboxRepository.findById(outbox.id)!!.status)
                .isEqualTo(PaymentOutboxStatus.COMPLETED)
            verify(exactly = 1) { paymentGateway.confirm(any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class `결제_승인_트랜잭션` {

        @Test
        fun `PG_승인_후_hold_소비에_실패하면_TX3의_내부_저장을_모두_롤백한다`() {
            val intentResult = customerReservationApplication.createReservationIntent(
                idempotencyKey = "test-intent-key",
                memberId = "customer-e2e",
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "TX3고객",
                guestPhone = "010-3333-3333",
                guestEmail = "tx3@test.com"
            )
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_mongo_tx3_rollback",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = intentResult.payment.amount.amount
            )

            customerReservationApplication.confirmReservationIntentPayment(
                memberId = "customer-e2e",
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_mongo_tx3_rollback",
                orderId = intentResult.payment.orderId,
                amount = intentResult.payment.amount.amount
            )
            inventoryHoldService.release(intentResult.intent.id)

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "mongo-tx3-rollback-worker")

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
            assertThat(reservationRepository.findPageByMemberId("customer-e2e", 0, 20).content).isEmpty()
            assertThat(mongoTemplate.getCollection("guests").countDocuments()).isZero()
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.RELEASED)
        }

        @Test
        fun `hold_소비_후_intent_저장이_충돌하면_TX3의_내부_저장과_hold_소비를_모두_롤백한다`() {
            val intentResult = customerReservationApplication.createReservationIntent(
                idempotencyKey = "test-intent-key",
                memberId = "customer-e2e",
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "TX3후반고객",
                guestPhone = "010-3333-3334",
                guestEmail = "tx3-late@test.com"
            )
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = "customer-e2e",
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_mongo_tx3_late_rollback",
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
                    paymentKey = "toss_pk_mongo_tx3_late_rollback",
                    orderId = intentResult.payment.orderId,
                    method = "카드",
                    approvedAt = Instant.now(clock),
                    totalAmount = intentResult.payment.amount.amount
                )
            }

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "mongo-tx3-late-rollback-worker")

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
            assertThat(reservationRepository.findPageByMemberId("customer-e2e", 0, 20).content).isEmpty()
            assertThat(mongoTemplate.getCollection("guests").countDocuments()).isZero()
        }

        @Test
        fun `내부_예약_확정이_3회_실패하면_결제_취소를_요청하고_PG_취소_성공_후_완료한다`() {
            val mutableClock = clock as MutableClock
            val intentResult = customerReservationApplication.createReservationIntent(
                idempotencyKey = "test-intent-key",
                memberId = "customer-e2e",
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "취소보상고객",
                guestPhone = "010-4444-4444",
                guestEmail = "compensation@test.com"
            )
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_mongo_compensation",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = intentResult.payment.amount.amount
            )
            every { paymentGateway.cancel(any(), any(), any()) } throws
                PaymentGatewayException.ProviderError(
                    "PROVIDER_ERROR",
                    "PG사 취소 일시 장애"
                )
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = "customer-e2e",
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_mongo_compensation",
                orderId = intentResult.payment.orderId,
                amount = intentResult.payment.amount.amount
            )
            inventoryHoldService.release(intentResult.intent.id)

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "mongo-compensation-worker-1")
            mutableClock.advance(Duration.ofSeconds(31))
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "mongo-compensation-worker-2")
            mutableClock.advance(Duration.ofSeconds(61))
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "mongo-compensation-worker-3")

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
            assertThat(reservationRepository.findPageByMemberId("customer-e2e", 0, 20).content).isEmpty()
            assertThat(mongoTemplate.getCollection("guests").countDocuments()).isZero()

            every { paymentGateway.cancel(any(), any(), any()) } returns
                PaymentCancelResult("toss_pk_mongo_compensation")
            mutableClock.advance(Duration.ofSeconds(31))

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "mongo-compensation-worker-4")

            assertThat(paymentRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(PaymentStatus.CANCELLED)
            assertThat(paymentOutboxRepository.findById(cancelOutbox.id)!!.status)
                .isEqualTo(PaymentOutboxStatus.COMPLETED)
            verify(exactly = 3) { paymentGateway.confirm(any(), any(), any(), any()) }
            verify(exactly = 2) { paymentGateway.cancel(any(), any(), any()) }
        }
    }

    @Nested
    inner class `재고_정합성` {

        @Test
        fun `예약_intent_생성_시_hold로_재고를_점유하고_결제_승인_worker_처리_후_예약_재고로_전환된다`() {
            // 예약 전 재고 확인
            val beforeInventory = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            val initialAvailable = beforeInventory[0].availableCount

            // 예약 intent 생성 (hold 재고 점유)
            val intentResult = customerReservationApplication.createReservationIntent(
                idempotencyKey = "test-intent-key",
                memberId = "customer-e2e",
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "E2E고객",
                guestPhone = "010-8888-8888",
                guestEmail = null
            )

            val afterIntent = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            assertThat(afterIntent[0].availableCount).isEqualTo(initialAvailable - 1)
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.HELD)

            // 결제 확인
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_inv", orderId = intentResult.payment.orderId,
                method = "카드", approvedAt = Instant.now(clock),
                totalAmount = BigDecimal(200_000)
            )
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = "customer-e2e",
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_inv",
                orderId = intentResult.payment.orderId,
                amount = BigDecimal(200_000)
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            val afterConfirm = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            assertThat(afterConfirm[0].availableCount).isEqualTo(initialAvailable - 1)
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.CONSUMED)
            val reservationId = reservationIntentRepository.findById(intentResult.intent.id)!!.reservationId!!

            // 취소 (재고 복원)
            every { paymentGateway.cancel("toss_pk_inv", any(), any()) } returns PaymentCancelResult("toss_pk_inv")
            customerReservationApplication.cancelReservation("customer-e2e", reservationId)
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            val afterCancel = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            assertThat(afterCancel[0].availableCount).isEqualTo(initialAvailable)
        }
    }

    @Nested
    inner class `재고_동시성` {

        @Test
        fun `두_고객이_마지막_1실을_동시에_결제하면_한_명만_hold와_최종_예약에_성공한다`() {
            memberRepository.save(
                Member.create(
                    id = "customer-e2e-2",
                    email = "e2e-2@test.com",
                    passwordHash = "hashed",
                    name = "E2E고객2",
                    role = MemberRole.CUSTOMER
                )
            )
            inventoryApplication.bulkBlock(
                propertyId = "prop-e2e",
                roomTypeId = "rt-e2e",
                startDate = checkIn,
                endDate = checkOut.minusDays(1),
                daysOfWeek = null,
                action = "BLOCK",
                count = 2
            )
            val executor = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val done = CountDownLatch(2)
            val successes = mutableListOf<Pair<String, String>>()
            val failures = mutableListOf<Throwable>()

            listOf("customer-e2e", "customer-e2e-2").forEachIndexed { index, memberId ->
                executor.submit {
                    try {
                        ready.countDown()
                        start.await(5, TimeUnit.SECONDS)
                        val result = customerReservationApplication.createReservationIntent(
                            idempotencyKey = "test-intent-key",
                            memberId = memberId,
                            propertyId = "prop-e2e",
                            roomTypeId = "rt-e2e",
                            checkIn = checkIn,
                            checkOut = checkOut,
                            numberOfGuests = 2,
                            guestName = "동시고객$index",
                            guestPhone = "010-5555-555$index",
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
            val (winnerMemberId, winnerIntentId) = successes.single()
            val winnerPayment = paymentRepository.findByReservationIntentId(winnerIntentId)!!
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_mongo_concurrent",
                orderId = winnerPayment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = winnerPayment.amount.amount
            )

            customerReservationApplication.confirmReservationIntentPayment(
                memberId = winnerMemberId,
                reservationIntentId = winnerIntentId,
                paymentKey = "toss_pk_mongo_concurrent",
                orderId = winnerPayment.orderId,
                amount = winnerPayment.amount.amount
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "mongo-concurrent-worker")

            val reservedIntent = reservationIntentRepository.findById(winnerIntentId)!!
            val reservations = reservationRepository.findPageByMemberId(winnerMemberId, 0, 20).content
            assertThat(reservedIntent.status).isEqualTo(ReservationIntentStatus.RESERVED)
            assertThat(reservations).hasSize(1)
            assertThat(reservations.single().id).isEqualTo(reservedIntent.reservationId)
            assertThat(paymentRepository.findByReservationIntentId(winnerIntentId)!!.status)
                .isEqualTo(PaymentStatus.APPROVED)
            assertThat(inventoryHoldRepository.findByReservationIntentId(winnerIntentId)!!.status)
                .isEqualTo(InventoryHoldStatus.CONSUMED)
        }
    }
}
