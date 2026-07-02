package com.stayops.reservation

import com.stayops.TestcontainersConfiguration
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.payment.application.required.PaymentConfirmResult
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.application.required.PaymentGateway
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.application.required.ReservationPaymentStatus
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
 * R-9-N #5: 예약 취소 시 N박 재고 복원 검증 통합 테스트.
 *
 * 검증 목표:
 * 1) 3박 예약 취소 시 정확히 3일치 재고가 모두 복원된다 (부분 복원 버그 방지)
 * 2) 인접한 다른 날짜의 재고는 영향받지 않는다
 * 3) Reservation 상태가 CANCELLED, Payment가 FAILED로 전환된다
 *
 * R-9-1과 한 쌍을 이루어 재고 정합성을 양방향으로 보장한다:
 * - R-9-1: 예약 생성 실패 시 차감된 재고 롤백 (부정 경로)
 * - R-9-N #5: 예약 취소 시 모든 차감 재고 복원 (긍정 경로)
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class CustomerReservationCancellationInventoryRestoreTest @Autowired constructor(
    private val customerReservationApplication: CustomerReservationApplication,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val memberRepository: MemberRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationIntentRepository: ReservationIntentRepository,
    private val paymentRepository: PaymentRepository,
    private val reservationPaymentOutboxApplication: ReservationPaymentOutboxApplication,
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock,
    @MockkBean private val paymentGateway: PaymentGateway
) {

    private val propertyId = "prop-cancel"
    private val roomTypeId = "rt-cancel"
    private val memberId = "customer-cancel"
    private lateinit var night1: LocalDate
    private lateinit var night2: LocalDate
    private lateinit var night3: LocalDate
    private lateinit var night4: LocalDate
    private lateinit var checkIn: LocalDate
    private lateinit var checkOut: LocalDate

    @BeforeEach
    fun setUp() {
        (clock as MutableClock).set(FixedTestClockConfig.DEFAULT_INSTANT)
        night1 = LocalDate.now(clock).plusDays(21)
        night2 = night1.plusDays(1)
        night3 = night1.plusDays(2)
        night4 = night1.plusDays(3)
        checkIn = night1
        checkOut = night4

        // 모든 컬렉션 cleanup
        mongoTemplate.collectionNames.forEach { name ->
            mongoTemplate.getCollection(name).deleteMany(org.bson.Document())
        }

        // Property
        propertyRepository.save(
            Property.create(
                id = propertyId, ownerId = "owner-1", name = "취소 테스트 호텔",
                type = PropertyType.HOTEL,
                address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
                contactInfo = ContactInfo.of("02-1234-5678", "cancel@test.com"),
                description = "취소 재고 복원 검증용", timezone = "Asia/Seoul", currency = "KRW"
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

        // Room 1개 → inventory 동기화 → totalCount=1, blockedCount=1
        roomRepository.save(Room.create("room-cancel-1", propertyId, roomTypeId, "101", 1))
        inventoryApplication.syncInventoryForRoomType(propertyId, roomTypeId)

        // night1~night4 모두 unblock → 4일 모두 가용 1
        val processed = inventoryApplication.bulkBlock(
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            startDate = night1,
            endDate = night4,
            daysOfWeek = null,
            action = "UNBLOCK",
            count = 1
        )
        assertThat(processed).isGreaterThan(0)

        // DIRECT Channel
        channelRepository.save(Channel.createDirect(id = "ch-cancel", propertyId = propertyId))

        // Member
        memberRepository.save(
            Member.create(
                id = memberId, email = "cancel@test.com",
                passwordHash = "hashed", name = "취소테스트고객",
                role = MemberRole.CUSTOMER
            )
        )
    }

    private fun availableCount(date: LocalDate): Int =
        inventoryApplication.getAvailability(propertyId, roomTypeId, date, date)[0].availableCount

    private fun reservedCount(date: LocalDate): Int =
        inventoryApplication.getAvailability(propertyId, roomTypeId, date, date)[0].reservedCount

    @Nested
    inner class `3박 확정 예약 취소 시` {

        @Test
        fun `예약 재고를 복원하고 인접 날짜는 영향받지 않는다`() {
            // Given: 4일 모두 가용 1
            assertThat(availableCount(night1)).isEqualTo(1)
            assertThat(availableCount(night2)).isEqualTo(1)
            assertThat(availableCount(night3)).isEqualTo(1)
            assertThat(availableCount(night4)).isEqualTo(1)

            // When: 3박 예약 intent 생성 (5/1~5/4 = night1, night2, night3)
            val intentResult = customerReservationApplication.createReservationIntent(
                memberId = memberId,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "취소테스트고객",
                guestPhone = "010-1234-5678",
                guestEmail = "cancel@test.com"
            )
            assertThat(intentResult.intent.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)

            // intent 생성 시에는 hold로만 점유한다.
            assertThat(reservedCount(night1)).isEqualTo(0)
            assertThat(reservedCount(night2)).isEqualTo(0)
            assertThat(reservedCount(night3)).isEqualTo(0)
            assertThat(availableCount(night1)).isEqualTo(0)
            assertThat(availableCount(night2)).isEqualTo(0)
            assertThat(availableCount(night3)).isEqualTo(0)
            assertThat(availableCount(night4))
                .withFailMessage("인접 날짜 night4가 영향받음")
                .isEqualTo(1)

            every { paymentGateway.confirm(any(), any(), any(), any()) } returns PaymentConfirmResult(
                paymentKey = "toss_pk_cancel",
                orderId = intentResult.payment.orderId,
                method = "카드",
                approvedAt = Instant.now(clock),
                totalAmount = BigDecimal(300_000)
            )
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = memberId,
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_cancel",
                orderId = intentResult.payment.orderId,
                amount = BigDecimal(300_000)
            )
            reservationPaymentOutboxApplication.processPendingMessages(workerId = "cancel-test-worker")

            val reservedIntent = reservationIntentRepository.findById(intentResult.intent.id)!!
            val reservationId = reservedIntent.reservationId!!
            assertThat(reservedIntent.status).isEqualTo(ReservationIntentStatus.RESERVED)
            assertThat(reservedCount(night1)).isEqualTo(1)
            assertThat(reservedCount(night2)).isEqualTo(1)
            assertThat(reservedCount(night3)).isEqualTo(1)

            // When: 확정 예약 취소
            val cancelled = customerReservationApplication.cancelReservation(memberId, reservationId)

            // Then: Reservation 상태 = CANCELLED
            assertThat(cancelled.reservation.status).isEqualTo(ReservationStatus.CANCELLED)

            // Then: Payment 상태 = CANCEL_REQUESTED (승인 결제 취소 outbox 요청)
            assertThat(cancelled.payment.status).isEqualTo(ReservationPaymentStatus.CANCEL_REQUESTED)

            // Then: 확정 예약 취소로 3박 재고가 모두 복원됨
            assertThat(reservedCount(night1))
                .withFailMessage("night1 재고가 복원되지 않음. reservedCount=%d", reservedCount(night1))
                .isEqualTo(0)
            assertThat(reservedCount(night2))
                .withFailMessage("night2 재고가 복원되지 않음. reservedCount=%d", reservedCount(night2))
                .isEqualTo(0)
            assertThat(reservedCount(night3))
                .withFailMessage("night3 재고가 복원되지 않음. reservedCount=%d", reservedCount(night3))
                .isEqualTo(0)
            assertThat(availableCount(night1)).isEqualTo(1)
            assertThat(availableCount(night2)).isEqualTo(1)
            assertThat(availableCount(night3)).isEqualTo(1)

            // Then: 인접 날짜 night4는 변화 없음
            assertThat(availableCount(night4))
                .withFailMessage("취소가 인접 날짜 night4에 영향을 미침")
                .isEqualTo(1)
            assertThat(reservedCount(night4)).isEqualTo(0)

            // Then: DB 상에 정확히 1건의 CANCELLED 예약만 존재
            val all = reservationRepository.findByPropertyId(propertyId)
            assertThat(all).hasSize(1)
            assertThat(all[0].status).isEqualTo(ReservationStatus.CANCELLED)

            // Then: Payment도 1건만 존재 (CANCEL_REQUESTED)
            val payments = paymentRepository.findByMemberId(memberId)
            assertThat(payments).hasSize(1)
            assertThat(payments[0].status).isEqualTo(PaymentStatus.CANCEL_REQUESTED)
        }
    }
}
