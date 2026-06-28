package com.stayops.reservation

import com.stayops.TestcontainersConfiguration
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.MutableClock
import com.stayops.shared.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.mongodb.core.MongoTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 예약 intent의 결제 가능 시간 만료를 Clock 추상화로 결정적으로 검증.
 *
 * 본 테스트는 Clock 추상화로:
 * 1) Clock.fixed가 아닌 MutableClock을 @Primary Bean으로 주입
 * 2) 예약 생성 시점의 시각으로 시작 (T0)
 * 3) Clock을 16분 진행 (T0 + 16분) — 실제 시간 흐름 시뮬레이션
 * 4) 만료된 intent의 결제 확인이 차단되고 hold 상태가 유지되는지 검증한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, CustomerReservationTtlExpirationClockTest.TestClockConfig::class)
class CustomerReservationTtlExpirationClockTest @Autowired constructor(
    private val customerReservationApplication: CustomerReservationApplication,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val memberRepository: MemberRepository,
    private val reservationIntentRepository: ReservationIntentRepository,
    private val inventoryHoldRepository: InventoryHoldRepository,
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock
) {

    /**
     * MutableClock을 Spring Primary Bean으로 등록하여 프로덕션의 SystemClock을 교체.
     * 이 Bean이 CustomerReservationApplication에 자동 주입된다.
     */
    @TestConfiguration
    class TestClockConfig {
        @Bean
        @Primary
        fun mutableClock(): Clock = MutableClock(initialInstant)

        companion object {
            // INVENTORY_HORIZON_DAYS=90 안의 시점을 기준으로 시작
            val initialInstant: Instant = Instant.parse("2026-05-15T10:00:00Z")
        }
    }

    private val propertyId = "prop-ttl"
    private val roomTypeId = "rt-ttl"
    private val memberId = "customer-ttl"
    private val checkIn = LocalDate.of(2026, 6, 1)
    private val checkOut = LocalDate.of(2026, 6, 2)

    @BeforeEach
    fun setUp() {
        // 시간을 초기값으로 리셋
        (clock as MutableClock).set(TestClockConfig.initialInstant)

        mongoTemplate.collectionNames.forEach { name ->
            mongoTemplate.getCollection(name).deleteMany(org.bson.Document())
        }

        propertyRepository.save(
            Property.create(
                id = propertyId, ownerId = "owner-1", name = "TTL 호텔",
                type = PropertyType.HOTEL,
                address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
                contactInfo = ContactInfo.of("02-1234-5678", "ttl@test.com"),
                description = "TTL 만료 검증용", timezone = "Asia/Seoul", currency = "KRW"
            ).activate()
        )

        roomTypeRepository.save(
            RoomType.create(
                id = roomTypeId, propertyId = propertyId, name = "스탠다드",
                description = "기본", maxOccupancy = 2,
                basePrice = Money.won(100_000)
            )
        )

        roomRepository.save(Room.create("room-ttl-1", propertyId, roomTypeId, "101", 1))
        inventoryApplication.syncInventoryForRoomType(propertyId, roomTypeId)
        inventoryApplication.bulkBlock(
            propertyId = propertyId, roomTypeId = roomTypeId,
            startDate = checkIn, endDate = checkOut.minusDays(1),
            daysOfWeek = null, action = "UNBLOCK", count = 1
        )

        channelRepository.save(Channel.createDirect(id = "ch-ttl", propertyId = propertyId))

        memberRepository.save(
            Member.create(
                id = memberId, email = "ttl@test.com",
                passwordHash = "hashed", name = "TTL테스트고객",
                role = MemberRole.CUSTOMER
            )
        )
    }

    @Test
    fun `Clock을 16분 진행하면 PAYMENT_WAITING intent 결제 확인이 만료로 차단되고 hold는 유지된다`() {
        val mutableClock = clock as MutableClock

        // Given: T0 = 2026-05-15 10:00:00 UTC, 재고 1실 가용
        val initialAvailable = inventoryApplication.getAvailability(
            propertyId, roomTypeId, checkIn, checkIn
        )[0].availableCount
        assertThat(initialAvailable).isEqualTo(1)

        // When: T0에 예약 intent 생성 → expiresAt = T0 + 15분 = 10:15:00
        val intentResult = customerReservationApplication.createReservationIntent(
            memberId = memberId,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            checkIn = checkIn,
            checkOut = checkOut,
            numberOfGuests = 2,
            guestName = "TTL테스트고객",
            guestPhone = "010-1234-5678",
            guestEmail = "ttl@test.com"
        )
        assertThat(intentResult.intent.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)
        assertThat(intentResult.intent.expiresAt)
            .withFailMessage("expiresAt이 Clock 기준 + 15분으로 정확히 계산되어야 함")
            .isEqualTo(TestClockConfig.initialInstant.plusSeconds(15 * 60))

        // intent 생성 직후: hold로 가용 재고를 점유함
        val afterIntent = inventoryApplication.getAvailability(
            propertyId, roomTypeId, checkIn, checkIn
        )[0]
        assertThat(afterIntent.reservedCount).isEqualTo(0)
        assertThat(afterIntent.heldCount).isEqualTo(1)
        assertThat(afterIntent.availableCount).isEqualTo(initialAvailable - 1)
        assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
            .isEqualTo(InventoryHoldStatus.HELD)

        // When: 16분 진행 후 결제 확인 시도
        mutableClock.advance(Duration.ofMinutes(16))

        assertThatThrownBy {
            customerReservationApplication.confirmReservationIntentPayment(
                memberId = memberId,
                reservationIntentId = intentResult.intent.id,
                paymentKey = "toss_pk_expired",
                orderId = intentResult.payment.orderId,
                amount = BigDecimal(100_000)
            )
        }.isInstanceOf(BusinessException::class.java)
            .hasMessage("결제 가능 시간이 만료되었습니다")

        // Then: 결제 확인 실패만으로는 intent/hold를 즉시 만료 처리하지 않는다
        val intentAfterExpiredPaymentAttempt = reservationIntentRepository.findById(intentResult.intent.id)!!
        assertThat(intentAfterExpiredPaymentAttempt.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)
        assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
            .isEqualTo(InventoryHoldStatus.HELD)

        // Then: hold가 유지되어 가용 재고는 점유된 상태 그대로 남는다
        val afterExpiry = inventoryApplication.getAvailability(
            propertyId, roomTypeId, checkIn, checkIn
        )[0]
        assertThat(afterExpiry.reservedCount).isEqualTo(0)
        assertThat(afterExpiry.heldCount).isEqualTo(1)
        assertThat(afterExpiry.availableCount).isEqualTo(initialAvailable - 1)
    }
}
