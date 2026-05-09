package com.stayops.reservation

import com.stayops.TestcontainersConfiguration
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.reservation.application.service.ReservationPaymentOutboxApplication
import com.stayops.payment.domain.model.PaymentCancelReason
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.service.PaymentConfirmResult
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.application.port.ReservationPaymentStatus
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.MutableClock
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * 결제 승인 시점 재고 차감 모델의 보상 처리 통합 테스트.
 *
 * 검증 목표:
 * 1) 예약 생성은 재고를 선점하지 않고 PENDING 예약과 결제만 만든다
 * 2) 결제 승인 worker가 재고 부족을 감지하면 부분 차감 재고를 복원하고 보상 취소 Outbox를 만든다
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class CustomerReservationTransactionRollbackTest @Autowired constructor(
    private val customerReservationApplication: CustomerReservationApplication,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val memberRepository: MemberRepository,
    private val reservationRepository: ReservationRepository,
    private val guestRepository: GuestRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentOutboxRepository: PaymentOutboxRepository,
    private val reservationPaymentOutboxApplication: ReservationPaymentOutboxApplication,
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock,
    @MockkBean private val paymentGateway: PaymentGateway
) {

    private val propertyId = "prop-rollback"
    private val roomTypeId = "rt-rollback"
    private val memberId = "customer-rollback"
    private lateinit var night1: LocalDate
    private lateinit var night2: LocalDate
    private lateinit var checkIn: LocalDate
    private lateinit var checkOut: LocalDate

    @BeforeEach
    fun setUp() {
        (clock as MutableClock).set(FixedTestClockConfig.DEFAULT_INSTANT)
        night1 = LocalDate.now(clock).plusDays(14)
        night2 = night1.plusDays(1)
        checkIn = night1
        checkOut = night1.plusDays(2)

        // 모든 컬렉션 cleanup
        mongoTemplate.collectionNames.forEach { name ->
            mongoTemplate.getCollection(name).deleteMany(org.bson.Document())
        }

        // Property (ACTIVE)
        propertyRepository.save(
            Property.create(
                id = propertyId, ownerId = "owner-1", name = "롤백 테스트 호텔",
                type = PropertyType.HOTEL,
                address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
                contactInfo = ContactInfo.of("02-1234-5678", "rollback@test.com"),
                description = "트랜잭션 롤백 검증용", timezone = "Asia/Seoul", currency = "KRW"
            ).activate()
        )

        // RoomType
        roomTypeRepository.save(
            RoomType.create(
                id = roomTypeId, propertyId = propertyId, name = "스탠다드룸",
                description = "기본 객실", maxOccupancy = 2,
                basePrice = Money.won(100_000)
            )
        )

        // Room 1개 → inventory 동기화 → totalCount=1, blockedCount=1, available=0
        roomRepository.save(Room.create("room-rb-1", propertyId, roomTypeId, "101", 1))
        inventoryApplication.syncInventoryForRoomType(propertyId, roomTypeId)

        // night1만 unblock → night1 가용 1, night2는 여전히 차단(가용 0)
        val processed = inventoryApplication.bulkBlock(
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            startDate = night1,
            endDate = night1, // night1만
            daysOfWeek = null,
            action = "UNBLOCK",
            count = 1
        )
        assertThat(processed).isGreaterThan(0)

        // DIRECT Channel
        channelRepository.save(Channel.createDirect(id = "ch-rb", propertyId = propertyId))

        // Member (CUSTOMER)
        memberRepository.save(
            Member.create(
                id = memberId, email = "rollback@test.com",
                passwordHash = "hashed", name = "롤백테스트고객",
                role = MemberRole.CUSTOMER
            )
        )
    }

    @Nested
    inner class `결제 승인 시점 재고 차감 중 재고 부족 시` {

        @Test
        fun `예약 생성은 재고를 차감하지 않고 PENDING 예약과 결제를 저장한다`() {
            // Given: night1 가용=1, night2 가용=0
            val night1Before = inventoryApplication.getAvailability(propertyId, roomTypeId, night1, night1)[0]
            val night2Before = inventoryApplication.getAvailability(propertyId, roomTypeId, night2, night2)[0]
            assertThat(night1Before.availableCount).isEqualTo(1)
            assertThat(night1Before.reservedCount).isEqualTo(0)
            assertThat(night2Before.availableCount).isEqualTo(0)

            // When: 2박 예약 생성
            val result = customerReservationApplication.createReservation(
                memberId = memberId,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "롤백테스트고객",
                guestPhone = "010-9999-9999",
                guestEmail = "rollback@test.com"
            )

            // Then: PENDING 예약과 결제는 저장되지만 재고는 변하지 않는다.
            assertThat(result.reservation.status).isEqualTo(ReservationStatus.PENDING)
            assertThat(result.payment.status).isEqualTo(ReservationPaymentStatus.PENDING)
            val night1After = inventoryApplication.getAvailability(propertyId, roomTypeId, night1, night1)[0]
            assertThat(night1After.reservedCount).isEqualTo(0)
            assertThat(night1After.availableCount).isEqualTo(1)
            val night2After = inventoryApplication.getAvailability(propertyId, roomTypeId, night2, night2)[0]
            assertThat(night2After.reservedCount).isEqualTo(0)
            assertThat(night2After.availableCount).isEqualTo(0)
        }

        @Test
        fun `결제 승인 worker가 재고 부족을 감지하면 예약 취소와 결제 취소 Outbox를 생성한다`() {
            // Given: night1=가용 1, night2=가용 0 (위 setUp과 동일)
            val result = customerReservationApplication.createReservation(
                memberId = memberId,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "롤백테스트고객",
                guestPhone = "010-9999-9999",
                guestEmail = "rollback@test.com"
            )
            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_shortage",
                orderId = result.payment.orderId,
                method = "카드",
                approvedAt = Instant.parse("2026-04-13T10:00:00Z"),
                totalAmount = BigDecimal(200_000),
                receiptUrl = null,
                cardNumber = null,
                cardCompany = null
            )

            // When: 승인 요청 접수 후 worker가 PG 승인 성공, night1 reserve 성공, night2 reserve 실패를 처리
            customerReservationApplication.confirmPayment(
                memberId = memberId,
                reservationId = result.reservation.id,
                paymentKey = "toss_pk_shortage",
                orderId = result.payment.orderId,
                amount = BigDecimal(200_000)
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "inventory-shortage-worker")

            // Then: 예약은 확정되지 않고 취소된다.
            val reservation = reservationRepository.findById(result.reservation.id)
            assertThat(reservation?.status).isEqualTo(ReservationStatus.CANCELLED)

            // Then: PG 승인 결제는 보상 취소 요청 상태가 되고 취소 Outbox가 남는다.
            val payment = paymentRepository.findByReservationId(result.reservation.id)
            assertThat(payment?.status).isEqualTo(PaymentStatus.CANCEL_REQUESTED)
            val cancelOutbox = paymentOutboxRepository.findByPaymentIdAndType(
                payment!!.id,
                PaymentOutboxType.CANCEL_PAYMENT
            )
            assertThat(cancelOutbox).isNotNull
            assertThat(cancelOutbox?.cancelReason).isEqualTo(PaymentCancelReason.INVENTORY_UNAVAILABLE.message)

            // Then: night1 부분 차감분은 복원되고, night2 재고는 그대로 유지된다.
            val night1After = inventoryApplication.getAvailability(propertyId, roomTypeId, night1, night1)[0]
            assertThat(night1After.reservedCount)
                .withFailMessage(
                    "night1 부분 차감 재고가 복원되지 않음. reservedCount=%d (예상: 0)",
                    night1After.reservedCount
                )
                .isEqualTo(0)
            assertThat(night1After.availableCount).isEqualTo(1)
            val night2After = inventoryApplication.getAvailability(propertyId, roomTypeId, night2, night2)[0]
            assertThat(night2After.reservedCount).isEqualTo(0)
            assertThat(night2After.availableCount).isEqualTo(0)
        }
    }
}
