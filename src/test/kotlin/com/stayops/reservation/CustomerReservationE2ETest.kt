package com.stayops.reservation

import com.stayops.TestcontainersConfiguration
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.reservation.application.port.ReservationPaymentStatus
import com.stayops.reservation.application.service.ReservationPaymentOutboxApplication
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.service.PaymentCancelResult
import com.stayops.payment.domain.service.PaymentConfirmResult
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.ConflictException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
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
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CustomerReservationE2ETest @Autowired constructor(
    private val customerReservationApplication: CustomerReservationApplication,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val memberRepository: MemberRepository,
    private val reservationRepository: ReservationRepository,
    private val paymentRepository: PaymentRepository,
    private val reservationPaymentOutboxApplication: ReservationPaymentOutboxApplication,
    private val mongoTemplate: MongoTemplate,
    @MockkBean private val paymentGateway: PaymentGateway
) {

    private val checkIn = LocalDate.of(2026, 5, 1)
    private val checkOut = LocalDate.of(2026, 5, 3)

    @BeforeEach
    fun setUp() {
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
        inventoryApplication.bulkBlock(
            propertyId = "prop-e2e",
            roomTypeId = "rt-e2e",
            startDate = checkIn,
            endDate = checkOut.minusDays(1),
            daysOfWeek = null,
            action = "UNBLOCK",
            count = 3
        )

        memberRepository.save(
            Member.create(
                id = "customer-e2e", email = "e2e@test.com",
                passwordHash = "hashed", name = "E2E고객",
                role = MemberRole.CUSTOMER
            )
        )
    }

    @Nested
    inner class `전체_예매_플로우` {

        @Test
        fun `예약_생성_결제_확인_취소_전체_흐름`() {
            // 1. 예약 생성
            val reservationResult = customerReservationApplication.createReservation(
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

            assertThat(reservationResult.reservation.status).isEqualTo(ReservationStatus.PENDING)
            assertThat(reservationResult.payment.status).isEqualTo(ReservationPaymentStatus.PENDING)
            assertThat(reservationResult.reservation.memberId).isEqualTo("customer-e2e")

            // 2. 결제 확인
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_e2e",
                orderId = reservationResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(),
                totalAmount = BigDecimal(200_000),
                receiptUrl = null, cardNumber = null, cardCompany = null
            )

            val requested = customerReservationApplication.confirmPayment(
                memberId = "customer-e2e",
                reservationId = reservationResult.reservation.id,
                paymentKey = "toss_pk_e2e",
                orderId = reservationResult.payment.orderId,
                amount = BigDecimal(200_000)
            )
            assertThat(requested.reservation.status).isEqualTo(ReservationStatus.PENDING)
            assertThat(requested.payment.status).isEqualTo(ReservationPaymentStatus.CONFIRM_REQUESTED)

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            val confirmedReservation = reservationRepository.findById(reservationResult.reservation.id)!!
            val confirmedPayment = paymentRepository.findByReservationId(reservationResult.reservation.id)!!

            assertThat(confirmedReservation.status).isEqualTo(ReservationStatus.PENDING)
            assertThat(confirmedPayment.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(confirmedPayment.paymentKey).isEqualTo("toss_pk_e2e")

            // 3. 마이페이지 조회
            val myReservations = customerReservationApplication.getMyReservations("customer-e2e")
            assertThat(myReservations).hasSize(1)
            assertThat(myReservations[0].reservation.id).isEqualTo(reservationResult.reservation.id)
            assertThat(myReservations[0].payment?.status).isEqualTo(ReservationPaymentStatus.APPROVED)

            // 4. 예약 취소 + 환불
            every { paymentGateway.cancel("toss_pk_e2e", any(), any()) } returns PaymentCancelResult("toss_pk_e2e")

            val cancelRequested = customerReservationApplication.cancelReservation(
                memberId = "customer-e2e",
                reservationId = reservationResult.reservation.id
            )

            assertThat(cancelRequested.reservation.status).isEqualTo(ReservationStatus.CANCELLED)
            assertThat(cancelRequested.payment.status).isEqualTo(ReservationPaymentStatus.CANCEL_REQUESTED)

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            val cancelledPayment = paymentRepository.findByReservationId(reservationResult.reservation.id)!!
            assertThat(cancelledPayment.status).isEqualTo(PaymentStatus.CANCELLED)
        }
    }

    @Nested
    inner class `중복_예약_방지` {

        @Test
        fun `동일_조건으로_두_번_예약하면_ConflictException_발생`() {
            // 첫 번째 예약 — 성공
            customerReservationApplication.createReservation(
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

            // 두 번째 예약 — 동일 조건 → ConflictException
            assertThatThrownBy {
                customerReservationApplication.createReservation(
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
        fun `결제가_이미_완료된_PENDING_예약에_confirmPayment를_다시_호출하면_기존_결과_반환`() {
            // 1. 예약 생성
            val reservationResult = customerReservationApplication.createReservation(
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
                orderId = reservationResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(),
                totalAmount = BigDecimal(200_000),
                receiptUrl = null, cardNumber = null, cardCompany = null
            )

            val firstResult = customerReservationApplication.confirmPayment(
                memberId = "customer-e2e",
                reservationId = reservationResult.reservation.id,
                paymentKey = "toss_pk_idempotent",
                orderId = reservationResult.payment.orderId,
                amount = BigDecimal(200_000)
            )

            assertThat(firstResult.reservation.status).isEqualTo(ReservationStatus.PENDING)
            assertThat(firstResult.payment.status).isEqualTo(ReservationPaymentStatus.CONFIRM_REQUESTED)

            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            // 3. 두 번째 결제 확인 — Toss 호출 없이 기존 결과 반환
            val secondResult = customerReservationApplication.confirmPayment(
                memberId = "customer-e2e",
                reservationId = reservationResult.reservation.id,
                paymentKey = "toss_pk_idempotent",
                orderId = reservationResult.payment.orderId,
                amount = BigDecimal(200_000)
            )

            assertThat(secondResult.reservation.status).isEqualTo(ReservationStatus.PENDING)
            assertThat(secondResult.payment.status).isEqualTo(ReservationPaymentStatus.APPROVED)
        }
    }

    @Nested
    inner class `재고_정합성` {

        @Test
        fun `예약_생성_후_재고는_유지되고_결제_승인_worker_처리_후_차감된다`() {
            // 예약 전 재고 확인
            val beforeInventory = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            val initialAvailable = beforeInventory[0].availableCount

            // 예약 생성 (재고 차감 없음)
            val reservationResult = customerReservationApplication.createReservation(
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

            val afterReservation = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            assertThat(afterReservation[0].availableCount).isEqualTo(initialAvailable)

            // 결제 확인
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_inv", orderId = reservationResult.payment.orderId,
                method = "카드", approvedAt = Instant.now(),
                totalAmount = BigDecimal(200_000),
                receiptUrl = null, cardNumber = null, cardCompany = null
            )
            customerReservationApplication.confirmPayment(
                memberId = "customer-e2e",
                reservationId = reservationResult.reservation.id,
                paymentKey = "toss_pk_inv",
                orderId = reservationResult.payment.orderId,
                amount = BigDecimal(200_000)
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            val afterConfirm = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            assertThat(afterConfirm[0].availableCount).isEqualTo(initialAvailable - 1)

            // 취소 (재고 복원)
            every { paymentGateway.cancel("toss_pk_inv", any(), any()) } returns PaymentCancelResult("toss_pk_inv")
            customerReservationApplication.cancelReservation("customer-e2e", reservationResult.reservation.id)
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "e2e-worker")

            val afterCancel = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            assertThat(afterCancel[0].availableCount).isEqualTo(initialAvailable)
        }
    }
}
