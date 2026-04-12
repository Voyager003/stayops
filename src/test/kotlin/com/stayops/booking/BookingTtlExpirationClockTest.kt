package com.stayops.booking

import com.stayops.TestcontainersConfiguration
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.booking.application.service.BookingApplication
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.payment.infrastructure.scheduler.PendingReservationScheduler
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.MutableClock
import com.ninjasquad.springmockk.MockkBean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * R-9 #10: PENDING 예약의 TTL 만료를 Clock 추상화로 결정적으로 검증.
 *
 * 기존 PendingExpirationE2ETest는 mongoTemplate.updateFirst로 expiresAt을
 * 직접 과거 시각으로 변경하는 우회 방법을 사용했다. 이는 도메인 모델 계약을
 * 우회하고 실제 시간 흐름을 시뮬레이션하지 못하는 한계가 있다.
 *
 * 본 테스트는 R-10-a에서 도입한 Clock 추상화의 첫 실제 활용 사례로:
 * 1) Clock.fixed가 아닌 MutableClock을 @Primary Bean으로 주입
 * 2) 예약 생성 시점의 시각으로 시작 (T0)
 * 3) Clock을 16분 진행 (T0 + 16분) — 실제 시간 흐름 시뮬레이션
 * 4) 스케줄러가 같은 Clock을 사용하므로 expiresAt > now 판단이 정확
 * 5) 만료된 예약이 자동 취소되고 재고가 복원되는지 검증
 *
 * 이 패턴은 mongoTemplate 우회 없이 도메인 객체의 정상 경로를 통해
 * 시간 시나리오를 검증하므로, R-10-a의 마이크로 PSA 가치를 입증한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, BookingTtlExpirationClockTest.TestClockConfig::class)
class BookingTtlExpirationClockTest @Autowired constructor(
    private val bookingApplication: BookingApplication,
    private val pendingReservationScheduler: PendingReservationScheduler,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val memberRepository: MemberRepository,
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock,
    @MockkBean private val paymentGateway: PaymentGateway
) {

    /**
     * MutableClock을 Spring Primary Bean으로 등록하여 프로덕션의 SystemClock을 교체.
     * 이 Bean이 BookingApplication, PendingReservationScheduler에 자동 주입된다.
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
    fun `Clock을 16분 진행하면 PENDING 예약이 자동 취소되고 재고가 복원된다`() {
        val mutableClock = clock as MutableClock

        // Given: T0 = 2026-05-15 10:00:00 UTC, 재고 1실 가용
        val initialAvailable = inventoryApplication.getAvailability(
            propertyId, roomTypeId, checkIn, checkIn
        )[0].availableCount
        assertThat(initialAvailable).isEqualTo(1)

        // When: T0에 PENDING 예약 생성 → expiresAt = T0 + 15분 = 10:15:00
        val booking = bookingApplication.createBooking(
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
        assertThat(booking.reservation.status).isEqualTo(ReservationStatus.PENDING)
        assertThat(booking.reservation.expiresAt)
            .withFailMessage("expiresAt이 Clock 기준 + 15분으로 정확히 계산되어야 함")
            .isEqualTo(TestClockConfig.initialInstant.plusSeconds(15 * 60))

        // 예약 직후: 재고 차감 확인
        val afterBooking = inventoryApplication.getAvailability(
            propertyId, roomTypeId, checkIn, checkIn
        )[0]
        assertThat(afterBooking.reservedCount).isEqualTo(1)
        assertThat(afterBooking.availableCount).isEqualTo(0)

        // When: 15분 미만 진행 (예: 14분) → 아직 만료 안 됨
        mutableClock.advance(Duration.ofMinutes(14))
        pendingReservationScheduler.expirePendingReservations()

        val reservationBeforeExpiry = mongoTemplate.findById(
            booking.reservation.id, org.bson.Document::class.java, "reservations"
        )
        assertThat(reservationBeforeExpiry!!.getString("status"))
            .withFailMessage("14분 경과 시점에는 아직 만료되지 않아야 함")
            .isEqualTo(ReservationStatus.PENDING.name)

        // When: 추가로 2분 진행 (총 16분 경과) → 만료
        mutableClock.advance(Duration.ofMinutes(2))
        pendingReservationScheduler.expirePendingReservations()

        // Then: 예약이 CANCELLED로 전환됨
        val reservationAfterExpiry = mongoTemplate.findById(
            booking.reservation.id, org.bson.Document::class.java, "reservations"
        )
        assertThat(reservationAfterExpiry!!.getString("status"))
            .withFailMessage(
                "16분 경과 시점에는 만료되어야 함. 현재 status=%s",
                reservationAfterExpiry.getString("status")
            )
            .isEqualTo(ReservationStatus.CANCELLED.name)

        // Then: 재고가 원상 복구됨
        val afterExpiry = inventoryApplication.getAvailability(
            propertyId, roomTypeId, checkIn, checkIn
        )[0]
        assertThat(afterExpiry.reservedCount).isEqualTo(0)
        assertThat(afterExpiry.availableCount).isEqualTo(initialAvailable)
    }
}
