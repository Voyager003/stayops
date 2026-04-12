package com.stayops.booking

import com.stayops.TestcontainersConfiguration
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.booking.application.service.BookingApplication
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import com.ninjasquad.springmockk.MockkBean
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.LocalDate

/**
 * R-9-1: createBooking @Transactional 롤백 검증 통합 테스트.
 *
 * 검증 목표:
 * 1) 다중 날짜 재고 차감 중 일부 날짜에서 실패하면, 이미 차감된 다른 날짜의 재고가 롤백되어 원상 복귀한다
 * 2) 재고 차감 도중 실패 시 Reservation/Guest/Payment 등 후속 단계가 실행되지 않는다
 *
 * 트랜잭션이 누락되거나 잘못 설정되면 첫 번째 검증이 실패하여 "유령 재고"(부분 차감 후 미복구) 버그가
 * 즉시 드러난다. 이는 단위 테스트로는 검증할 수 없는 영역이다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class BookingTransactionRollbackTest @Autowired constructor(
    private val bookingApplication: BookingApplication,
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val memberRepository: MemberRepository,
    private val reservationRepository: ReservationRepository,
    private val guestRepository: GuestRepository,
    private val paymentRepository: PaymentRepository,
    private val mongoTemplate: MongoTemplate,
    @MockkBean private val paymentGateway: PaymentGateway
) {

    private val propertyId = "prop-rollback"
    private val roomTypeId = "rt-rollback"
    private val memberId = "customer-rollback"
    private val night1 = LocalDate.of(2026, 6, 1)
    private val night2 = LocalDate.of(2026, 6, 2)
    private val checkIn = night1
    private val checkOut = LocalDate.of(2026, 6, 3) // 2박 (night1, night2)

    @BeforeEach
    fun setUp() {
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
        inventoryApplication.bulkBlock(
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            startDate = night1,
            endDate = night1, // night1만
            daysOfWeek = null,
            action = "UNBLOCK",
            count = 1
        )

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
    inner class `다중 날짜 예약 중 중간 실패 시` {

        @Test
        fun `이미 차감된 첫 날 재고가 롤백되어 원상 복귀한다`() {
            // Given: night1 가용=1, night2 가용=0
            val night1Before = inventoryApplication.getAvailability(propertyId, roomTypeId, night1, night1)[0]
            val night2Before = inventoryApplication.getAvailability(propertyId, roomTypeId, night2, night2)[0]
            assertThat(night1Before.availableCount).isEqualTo(1)
            assertThat(night1Before.reservedCount).isEqualTo(0)
            assertThat(night2Before.availableCount).isEqualTo(0)

            // When: 2박 예약 시도 → night1 reserve 성공, night2 reserve 실패 → 트랜잭션 롤백
            assertThatThrownBy {
                bookingApplication.createBooking(
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
            }.isInstanceOf(Throwable::class.java)

            // Then: night1 재고가 원상 복귀해야 함 (롤백되지 않으면 reservedCount = 1로 남음 = 유령 재고 버그)
            val night1After = inventoryApplication.getAvailability(propertyId, roomTypeId, night1, night1)[0]
            assertThat(night1After.reservedCount)
                .withFailMessage("night1 재고가 롤백되지 않음. reservedCount=%d (예상: 0)", night1After.reservedCount)
                .isEqualTo(0)
            assertThat(night1After.availableCount).isEqualTo(1)

            // Then: night2 재고는 그대로
            val night2After = inventoryApplication.getAvailability(propertyId, roomTypeId, night2, night2)[0]
            assertThat(night2After.reservedCount).isEqualTo(0)
            assertThat(night2After.availableCount).isEqualTo(0)
        }

        @Test
        fun `Reservation, Guest, Payment 가 저장되지 않는다`() {
            // Given: night1=가용 1, night2=가용 0 (위 setUp과 동일)

            // When: 2박 예약 시도 → 실패
            runCatching {
                bookingApplication.createBooking(
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
            }

            // Then: 어떤 Reservation도 저장되지 않음
            val reservations = reservationRepository.findByPropertyId(propertyId)
            assertThat(reservations)
                .withFailMessage("Reservation이 롤백되지 않고 저장됨: %s", reservations)
                .isEmpty()

            // Then: Guest도 저장되지 않음 (createBooking 단계 6에서 저장 시도하지만 단계 5에서 실패)
            // 정확히는 night1 reserve 후 night2 reserve에서 실패하므로 단계 6은 도달 못 함
            // 다만 트랜잭션 롤백에 의해 어떤 Guest도 남지 않아야 함
            val guests = guestRepository.findByPropertyId(propertyId)
            assertThat(guests)
                .withFailMessage("Guest가 롤백되지 않고 저장됨: %s", guests)
                .isEmpty()

            // Then: Payment도 저장되지 않음
            val payments = paymentRepository.findByMemberId(memberId)
            assertThat(payments)
                .withFailMessage("Payment가 롤백되지 않고 저장됨: %s", payments)
                .isEmpty()
        }
    }
}
