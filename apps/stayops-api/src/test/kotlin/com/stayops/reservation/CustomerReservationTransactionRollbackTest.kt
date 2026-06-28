package com.stayops.reservation

import com.stayops.TestcontainersConfiguration
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Clock
import java.time.LocalDate

/**
 * 예약 intent 생성 시점 재고 hold 모델의 트랜잭션 롤백 통합 테스트.
 *
 * 검증 목표:
 * 1) intent 생성은 전체 숙박일 재고를 hold 할 수 있을 때만 성공한다
 * 2) 중간 날짜의 재고 부족으로 실패하면 앞 날짜의 부분 hold, intent, payment가 남지 않는다
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
    private val reservationIntentRepository: ReservationIntentRepository,
    private val inventoryHoldRepository: InventoryHoldRepository,
    private val paymentRepository: PaymentRepository,
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock
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
    inner class `예약 intent 생성 중 재고 부족 시` {

        @Test
        fun `전체 날짜 재고가 부족하면 intent와 payment를 생성하지 않고 부분 hold를 롤백한다`() {
            // Given: night1 가용=1, night2 가용=0
            val night1Before = inventoryApplication.getAvailability(propertyId, roomTypeId, night1, night1)[0]
            val night2Before = inventoryApplication.getAvailability(propertyId, roomTypeId, night2, night2)[0]
            assertThat(night1Before.availableCount).isEqualTo(1)
            assertThat(night1Before.reservedCount).isEqualTo(0)
            assertThat(night2Before.availableCount).isEqualTo(0)

            assertThatThrownBy {
                customerReservationApplication.createReservationIntent(
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
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("가용 객실이 없습니다")

            // Then: night1 부분 hold는 롤백되고, night2 재고는 그대로 유지된다.
            val night1After = inventoryApplication.getAvailability(propertyId, roomTypeId, night1, night1)[0]
            assertThat(night1After.heldCount)
                .withFailMessage(
                    "night1 부분 hold가 롤백되지 않음. heldCount=%d (예상: 0)",
                    night1After.heldCount
                )
                .isEqualTo(0)
            assertThat(night1After.availableCount).isEqualTo(1)
            val night2After = inventoryApplication.getAvailability(propertyId, roomTypeId, night2, night2)[0]
            assertThat(night2After.reservedCount).isEqualTo(0)
            assertThat(night2After.heldCount).isEqualTo(0)
            assertThat(night2After.availableCount).isEqualTo(0)

            assertThat(reservationRepository.findPageByMemberId(memberId, 0, 20).content).isEmpty()
            assertThat(paymentRepository.findByMemberId(memberId)).isEmpty()
            assertThat(
                inventoryHoldRepository.findActiveByPropertyIdAndRoomTypeIdAndDates(
                    propertyId = propertyId,
                    roomTypeId = roomTypeId,
                    dates = listOf(night1, night2),
                    now = clock.instant()
                )
            ).isEmpty()
            assertThat(
                reservationIntentRepository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
                    memberId = memberId,
                    roomTypeId = roomTypeId,
                    checkIn = checkIn,
                    checkOut = checkOut,
                    now = clock.instant()
                )
            ).isFalse()
        }
    }
}
