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
import com.ninjasquad.springmockk.MockkBean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PendingExpirationE2ETest @Autowired constructor(
    private val bookingApplication: BookingApplication,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val memberRepository: MemberRepository,
    private val pendingReservationScheduler: PendingReservationScheduler,
    private val mongoTemplate: MongoTemplate,
    @MockkBean private val paymentGateway: PaymentGateway
) {

    private val checkIn = LocalDate.of(2026, 5, 1)
    private val checkOut = LocalDate.of(2026, 5, 2)

    @BeforeEach
    fun setUp() {
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
        inventoryApplication.bulkBlock(
            propertyId = "prop-exp",
            roomTypeId = "rt-exp",
            startDate = checkIn,
            endDate = checkOut.minusDays(1),
            daysOfWeek = null,
            action = "UNBLOCK",
            count = 1
        )

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
    inner class `PENDING_만료_처리` {

        @Test
        fun `PENDING_만료_시_예약이_취소되고_재고가_복원된다`() {
            // Given: 예약 전 재고 확인
            val beforeInventory = inventoryApplication.getAvailability(
                "prop-exp", "rt-exp", checkIn, checkIn
            )
            val initialAvailable = beforeInventory[0].availableCount
            assertThat(initialAvailable).isEqualTo(1)

            // Create booking -> PENDING
            val booking = bookingApplication.createBooking(
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
            assertThat(booking.reservation.status).isEqualTo(ReservationStatus.PENDING)

            // Verify: reservedCount increased, availableCount decreased
            val afterBooking = inventoryApplication.getAvailability(
                "prop-exp", "rt-exp", checkIn, checkIn
            )
            assertThat(afterBooking[0].availableCount).isEqualTo(initialAvailable - 1)
            assertThat(afterBooking[0].reservedCount).isEqualTo(1)

            // Manually set expiresAt to past via MongoTemplate
            mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").`is`(booking.reservation.id)),
                Update.update("expiresAt", Instant.now().minusSeconds(3600)),
                "reservations"
            )

            // When: run scheduler
            pendingReservationScheduler.expirePendingReservations()

            // Then: reservation should be CANCELLED
            val reservationDoc = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").`is`(booking.reservation.id)),
                org.bson.Document::class.java,
                "reservations"
            )
            assertThat(reservationDoc).isNotNull
            assertThat(reservationDoc!!.getString("status")).isEqualTo(ReservationStatus.CANCELLED.name)

            // Then: inventory should be restored to pre-booking value
            val afterExpiry = inventoryApplication.getAvailability(
                "prop-exp", "rt-exp", checkIn, checkIn
            )
            assertThat(afterExpiry[0].availableCount).isEqualTo(initialAvailable)
        }
    }
}
