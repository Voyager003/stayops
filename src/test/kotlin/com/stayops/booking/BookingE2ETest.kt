package com.stayops.booking

import com.stayops.TestcontainersConfiguration
import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.auth.domain.repository.MemberRepository
import com.stayops.booking.application.service.BookingApplication
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
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
class BookingE2ETest @Autowired constructor(
    private val bookingApplication: BookingApplication,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val memberRepository: MemberRepository,
    private val reservationRepository: ReservationRepository,
    private val paymentRepository: PaymentRepository,
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
            val booking = bookingApplication.createBooking(
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

            assertThat(booking.reservation.status).isEqualTo(ReservationStatus.PENDING)
            assertThat(booking.payment.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(booking.reservation.memberId).isEqualTo("customer-e2e")

            // 2. 결제 확인
            every { paymentGateway.confirm(any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_e2e",
                orderId = booking.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(),
                totalAmount = BigDecimal(200_000),
                receiptUrl = null, cardNumber = null, cardCompany = null
            )

            val confirmed = bookingApplication.confirmPayment(
                memberId = "customer-e2e",
                reservationId = booking.reservation.id,
                paymentKey = "toss_pk_e2e",
                orderId = booking.payment.orderId,
                amount = BigDecimal(200_000)
            )

            assertThat(confirmed.reservation.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(confirmed.payment.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(confirmed.payment.paymentKey).isEqualTo("toss_pk_e2e")

            // 3. 마이페이지 조회
            val myReservations = bookingApplication.getMyReservations("customer-e2e")
            assertThat(myReservations).hasSize(1)
            assertThat(myReservations[0].id).isEqualTo(booking.reservation.id)

            // 4. 예약 취소 + 환불
            every { paymentGateway.cancel("toss_pk_e2e", any()) } returns PaymentCancelResult("toss_pk_e2e")

            val cancelled = bookingApplication.cancelBooking(
                memberId = "customer-e2e",
                reservationId = booking.reservation.id
            )

            assertThat(cancelled.reservation.status).isEqualTo(ReservationStatus.CANCELLED)
            assertThat(cancelled.payment.status).isEqualTo(PaymentStatus.CANCELLED)
        }
    }

    @Nested
    inner class `중복_예약_방지` {

        @Test
        fun `동일_조건으로_두_번_예약하면_ConflictException_발생`() {
            // 첫 번째 예약 — 성공
            bookingApplication.createBooking(
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
                bookingApplication.createBooking(
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
        fun `이미_CONFIRMED된_예약에_confirmPayment를_다시_호출하면_기존_결과_반환`() {
            // 1. 예약 생성
            val booking = bookingApplication.createBooking(
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
            every { paymentGateway.confirm(any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_idempotent",
                orderId = booking.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(),
                totalAmount = BigDecimal(200_000),
                receiptUrl = null, cardNumber = null, cardCompany = null
            )

            val firstResult = bookingApplication.confirmPayment(
                memberId = "customer-e2e",
                reservationId = booking.reservation.id,
                paymentKey = "toss_pk_idempotent",
                orderId = booking.payment.orderId,
                amount = BigDecimal(200_000)
            )

            assertThat(firstResult.reservation.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(firstResult.payment.status).isEqualTo(PaymentStatus.APPROVED)

            // 3. 두 번째 결제 확인 — Toss 호출 없이 기존 결과 반환
            val secondResult = bookingApplication.confirmPayment(
                memberId = "customer-e2e",
                reservationId = booking.reservation.id,
                paymentKey = "toss_pk_idempotent",
                orderId = booking.payment.orderId,
                amount = BigDecimal(200_000)
            )

            assertThat(secondResult.reservation.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(secondResult.payment.status).isEqualTo(PaymentStatus.APPROVED)
        }
    }

    @Nested
    inner class `재고_정합성` {

        @Test
        fun `예약_후_재고_차감_취소_후_재고_복원`() {
            // 예약 전 재고 확인
            val beforeInventory = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            val initialAvailable = beforeInventory[0].availableCount

            // 예약 생성 (재고 차감)
            val booking = bookingApplication.createBooking(
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

            val afterBooking = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            assertThat(afterBooking[0].availableCount).isEqualTo(initialAvailable - 1)

            // 결제 확인
            every { paymentGateway.confirm(any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_inv", orderId = booking.payment.orderId,
                method = "카드", approvedAt = Instant.now(),
                totalAmount = BigDecimal(200_000),
                receiptUrl = null, cardNumber = null, cardCompany = null
            )
            bookingApplication.confirmPayment(
                memberId = "customer-e2e",
                reservationId = booking.reservation.id,
                paymentKey = "toss_pk_inv",
                orderId = booking.payment.orderId,
                amount = BigDecimal(200_000)
            )

            // 취소 (재고 복원)
            every { paymentGateway.cancel("toss_pk_inv", any()) } returns PaymentCancelResult("toss_pk_inv")
            bookingApplication.cancelBooking("customer-e2e", booking.reservation.id)

            val afterCancel = inventoryApplication.getAvailability(
                "prop-e2e", "rt-e2e", checkIn, checkIn.plusDays(1)
            )
            assertThat(afterCancel[0].availableCount).isEqualTo(initialAvailable)
        }
    }
}
