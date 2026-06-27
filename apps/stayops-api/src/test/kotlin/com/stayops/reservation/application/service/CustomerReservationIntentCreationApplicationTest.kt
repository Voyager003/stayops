package com.stayops.reservation.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.provided.InventoryHoldService
import com.stayops.inventory.application.provided.InventoryHoldSnapshot
import com.stayops.property.domain.model.Address
import com.stayops.property.domain.model.ContactInfo
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.application.required.ReservationPaymentSnapshot
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CustomerReservationIntentCreationApplicationTest : BehaviorSpec({

    val propertyRepository = mockk<PropertyRepository>()
    val roomTypeRepository = mockk<RoomTypeRepository>()
    val channelRepository = mockk<ChannelRepository>()
    val ratePlanRepository = mockk<RatePlanRepository>()
    val reservationRepository = mockk<ReservationRepository>()
    val reservationIntentRepository = mockk<ReservationIntentRepository>()
    val inventoryHoldService = mockk<InventoryHoldService>()
    val reservationPaymentService = mockk<ReservationPaymentService>()
    val rateResolverService = RateResolverService()
    val fixedInstant = Instant.parse("2026-04-08T10:00:00Z")
    val clock = Clock.fixed(fixedInstant, ZoneId.of("Asia/Seoul"))
    val generatedIds = ArrayDeque(listOf("intent-1"))
    val idGenerator = object : IdGenerator {
        override fun generate(): String = generatedIds.removeFirst()
    }
    val sut = CustomerReservationIntentCreationApplication(
        propertyRepository = propertyRepository,
        roomTypeRepository = roomTypeRepository,
        channelRepository = channelRepository,
        ratePlanRepository = ratePlanRepository,
        reservationRepository = reservationRepository,
        reservationIntentRepository = reservationIntentRepository,
        inventoryHoldService = inventoryHoldService,
        reservationPaymentService = reservationPaymentService,
        rateResolverService = rateResolverService,
        clock = clock,
        idGenerator = idGenerator
    )

    val checkIn = LocalDate.of(2026, 4, 1)
    val checkOut = LocalDate.of(2026, 4, 3)
    val expiresAt = fixedInstant.plusSeconds(15 * 60)

    fun activeProperty(): Property =
        Property.create(
            id = "prop-1",
            ownerId = "owner-1",
            name = "테스트 호텔",
            type = PropertyType.HOTEL,
            address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
            contactInfo = ContactInfo.of("02-1234-5678", "hotel@test.com"),
            description = "테스트 호텔",
            timezone = "Asia/Seoul",
            currency = "KRW"
        ).activate()

    fun activeRoomType(): RoomType =
        RoomType.create(
            id = "rt-1",
            propertyId = "prop-1",
            name = "디럭스룸",
            description = "넓은 객실",
            maxOccupancy = 2,
            basePrice = Money.won(100_000)
        )

    fun directChannel(): Channel = Channel.createDirect(id = "ch-1", propertyId = "prop-1")

    fun pendingPayment(): ReservationPaymentSnapshot =
        ReservationPaymentSnapshot(
            id = "pay-1",
            reservationId = null,
            reservationIntentId = "intent-1",
            memberId = "member-1",
            orderId = "STAYOPS-intent-1-test",
            amount = Money.won(200_000),
            status = ReservationPaymentStatus.PENDING,
            paymentKey = null,
            failReason = null
        )

    beforeTest {
        generatedIds.clear()
        generatedIds += "intent-1"
    }

    given("예약 intent 생성 시") {
        `when`("예약 조건이 유효하면") {
            val savedIntent = slot<ReservationIntent>()
            every {
                reservationRepository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
                    "member-1",
                    "rt-1",
                    checkIn,
                    checkOut,
                    fixedInstant
                )
            } returns false
            every { propertyRepository.findById("prop-1") } returns activeProperty()
            every { roomTypeRepository.findById("rt-1") } returns activeRoomType()
            every { channelRepository.findByPropertyIdAndCode("prop-1", "DIRECT") } returns directChannel()
            every {
                ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
                    "prop-1",
                    "rt-1",
                    RatePlanStatus.ACTIVE
                )
            } returns emptyList()
            every {
                inventoryHoldService.hold(
                    reservationIntentId = "intent-1",
                    propertyId = "prop-1",
                    roomTypeId = "rt-1",
                    dateRange = DateRange.of(checkIn, checkOut),
                    quantity = 1,
                    expiresAt = expiresAt
                )
            } returns InventoryHoldSnapshot(
                id = "hold-1",
                reservationIntentId = "intent-1",
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                dates = DateRange.of(checkIn, checkOut).allDates(),
                quantity = 1,
                expiresAt = expiresAt
            )
            every {
                reservationPaymentService.createPendingPaymentForReservationIntent(
                    reservationIntentId = "intent-1",
                    memberId = "member-1",
                    amount = Money.won(200_000)
                )
            } returns pendingPayment()
            every { reservationIntentRepository.save(capture(savedIntent)) } answers { firstArg() }
            every { reservationRepository.save(any()) } answers { firstArg() }

            val result = sut.createReservationIntent(
                memberId = "member-1",
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                checkIn = checkIn,
                checkOut = checkOut,
                numberOfGuests = 2,
                guestName = "김고객",
                guestPhone = "010-1111-2222",
                guestEmail = "kim@test.com"
            )

            then("Reservation 없이 intent, hold, pending payment를 생성한다") {
                result.intent.status shouldBe ReservationIntentStatus.PAYMENT_WAITING
                result.intent.paymentId shouldBe "pay-1"
                result.intent.holdId shouldBe "hold-1"
                result.payment.reservationId shouldBe null
                result.payment.reservationIntentId shouldBe "intent-1"
                savedIntent.captured.pricing.totalAmount.amount shouldBe BigDecimal("200000")
                verify(exactly = 0) { reservationRepository.save(any()) }
            }
        }
    }
})
