package com.stayops.e2e.reservation

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
import com.stayops.reservation.application.service.ReservationIntentExpirationApplication
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class PendingExpirationE2ETest @Autowired constructor(
    private val customerReservationApplication: CustomerReservationApplication,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val memberRepository: MemberRepository,
    private val reservationIntentRepository: ReservationIntentRepository,
    private val inventoryHoldRepository: InventoryHoldRepository,
    private val expirationApplication: ReservationIntentExpirationApplication,
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock
) {

    private lateinit var checkIn: LocalDate
    private lateinit var checkOut: LocalDate

    @BeforeEach
    fun setUp() {
        (clock as MutableClock).set(FixedTestClockConfig.DEFAULT_INSTANT)
        checkIn = LocalDate.now(clock).plusDays(7)
        checkOut = checkIn.plusDays(1)

        mongoTemplate.collectionNames.forEach { name ->
            mongoTemplate.getCollection(name).deleteMany(org.bson.Document())
        }

        // Property (ACTIVE)
        propertyRepository.save(
            Property.create(
                id = "prop-exp", ownerId = "owner-1", name = "Expiration 호텔",
                type = PropertyType.HOTEL,
                address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
                contactInfo = ContactInfo.of("02-1234-5678", "exp@test.com"),
                description = "만료 테스트 호텔", timezone = "Asia/Seoul", currency = "KRW"
            ).activate()
        )

        // RoomType
        roomTypeRepository.save(
            RoomType.create(
                id = "rt-exp", propertyId = "prop-exp", name = "스탠다드룸",
                description = "기본 객실", maxOccupancy = 2,
                basePrice = Money.won(100_000)
            )
        )

        // Room + inventory sync
        roomRepository.save(Room.create("room-exp-1", "prop-exp", "rt-exp", "101", 1))
        inventoryApplication.syncInventoryForRoomType("prop-exp", "rt-exp")

        // Unblock inventory for test dates
        val processed = inventoryApplication.bulkBlock(
            propertyId = "prop-exp",
            roomTypeId = "rt-exp",
            startDate = checkIn,
            endDate = checkOut.minusDays(1),
            daysOfWeek = null,
            action = "UNBLOCK",
            count = 1
        )
        assertThat(processed).isGreaterThan(0)

        // DIRECT Channel
        channelRepository.save(Channel.createDirect(id = "ch-exp", propertyId = "prop-exp"))

        // Member (CUSTOMER)
        memberRepository.save(
            Member.create(
                id = "customer-exp", email = "exp@test.com",
                passwordHash = "hashed", name = "만료테스트고객",
                role = MemberRole.CUSTOMER
            )
        )
    }

    @Nested
    inner class `예약_intent_만료_처리` {

        @Test
        fun `만료된_PAYMENT_WAITING_intent는_EXPIRED로_변경되고_hold를_해제해_새_intent를_막지_않는다`() {
            // Given: 예약 전 재고 확인
            val beforeInventory = inventoryApplication.getAvailability(
                "prop-exp", "rt-exp", checkIn, checkIn
            )
            val initialAvailable = beforeInventory[0].availableCount
            assertThat(initialAvailable).isEqualTo(1)

            val intentResult = customerReservationApplication.createReservationIntent(
                memberId = "customer-exp",
                propertyId = "prop-exp",
                roomTypeId = "rt-exp",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "만료테스트고객",
                guestPhone = "010-1111-1111",
                guestEmail = "exp@test.com"
            )
            assertThat(intentResult.intent.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)

            val afterIntent = inventoryApplication.getAvailability(
                "prop-exp", "rt-exp", checkIn, checkIn
            )
            assertThat(afterIntent[0].availableCount).isEqualTo(initialAvailable - 1)
            assertThat(afterIntent[0].heldCount).isEqualTo(1)
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.HELD)

            (clock as MutableClock).advance(Duration.ofMinutes(16))
            val expiredCount = expirationApplication.expirePaymentWaitingIntents()

            assertThat(expiredCount).isEqualTo(1)
            assertThat(reservationIntentRepository.findById(intentResult.intent.id)!!.status)
                .isEqualTo(ReservationIntentStatus.EXPIRED)
            assertThat(inventoryHoldRepository.findByReservationIntentId(intentResult.intent.id)!!.status)
                .isEqualTo(InventoryHoldStatus.RELEASED)

            val afterExpiration = inventoryApplication.getAvailability(
                "prop-exp", "rt-exp", checkIn, checkIn
            )
            assertThat(afterExpiration[0].availableCount).isEqualTo(initialAvailable)
            assertThat(afterExpiration[0].heldCount).isEqualTo(0)
            assertThat(afterExpiration[0].reservedCount).isEqualTo(0)

            val secondIntent = customerReservationApplication.createReservationIntent(
                memberId = "customer-exp",
                propertyId = "prop-exp",
                roomTypeId = "rt-exp",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "만료테스트고객",
                guestPhone = "010-1111-1111",
                guestEmail = "exp@test.com"
            )

            assertThat(secondIntent.intent.status).isEqualTo(ReservationIntentStatus.PAYMENT_WAITING)

            val afterSecondIntent = inventoryApplication.getAvailability(
                "prop-exp", "rt-exp", checkIn, checkIn
            )
            assertThat(afterSecondIntent[0].availableCount).isEqualTo(initialAvailable - 1)
            assertThat(afterSecondIntent[0].heldCount).isEqualTo(1)
            assertThat(afterSecondIntent[0].reservedCount).isEqualTo(0)
        }
    }
}
