package com.stayops.reservation.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.reservation.application.port.ReservationPaymentPort
import com.stayops.reservation.application.port.ReservationPaymentSnapshot
import com.stayops.reservation.application.port.ReservationPaymentStatus
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.LocalDate

class ReservationApplicationTest : BehaviorSpec({

    val reservationRepository = mockk<ReservationRepository>()
    val roomTypeRepository = mockk<RoomTypeRepository>()
    val channelRepository = mockk<ChannelRepository>()
    val ratePlanRepository = mockk<RatePlanRepository>()
    val guestRepository = mockk<GuestRepository>()
    val inventoryReservationPort = mockk<InventoryReservationPort>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val rateResolverService = RateResolverService()
    val reservationPaymentPort = mockk<ReservationPaymentPort>()
    val reservationCancellationPolicy = ReservationCancellationPolicy(reservationPaymentPort)
    val idGenerator = object : IdGenerator {
        override fun generate(): String = "test-id"
    }

    val sut = ReservationApplication(
        reservationRepository = reservationRepository,
        roomTypeRepository = roomTypeRepository,
        channelRepository = channelRepository,
        ratePlanRepository = ratePlanRepository,
        guestRepository = guestRepository,
        inventoryReservationPort = inventoryReservationPort,
        eventPublisher = eventPublisher,
        rateResolverService = rateResolverService,
        reservationPaymentPort = reservationPaymentPort,
        reservationCancellationPolicy = reservationCancellationPolicy,
        idGenerator = idGenerator
    )

    val checkIn = LocalDate.of(2026, 4, 1)
    val checkOut = LocalDate.of(2026, 4, 3)

    fun sampleRoomType() = RoomType.create(
        id = "rt-1",
        propertyId = "prop-1",
        name = "디럭스 더블",
        description = "넓은 객실",
        maxOccupancy = 3,
        basePrice = Money.won(100_000)
    )

    fun directChannel() = Channel.createDirect(id = "ch-0", propertyId = "prop-1")

    fun otaChannel() = Channel.createOta(
        id = "ch-1",
        propertyId = "prop-1",
        code = "AGODA",
        name = "Agoda",
        commissionRate = BigDecimal("0.15"),
        apiEndpoint = "https://mock-ota/ari"
    )

    fun sampleGuest() = Guest.create(
        id = "guest-1",
        propertyId = "prop-1",
        name = "홍길동",
        phone = "010-1234-5678",
        email = "hong@test.com"
    )

    // -- 예약 생성: 정상 --

    given("예약 생성 시") {
        `when`("유효한 DIRECT 예약이면") {
            then("PENDING 상태의 예약이 생성된다") {
                clearAllMocks()
                every { roomTypeRepository.findById("rt-1") } returns sampleRoomType()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "DIRECT") } returns directChannel()
                every { ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus("prop-1", "rt-1", RatePlanStatus.ACTIVE) } returns emptyList()
                justRun { inventoryReservationPort.reserve("prop-1", "rt-1", any()) }
                every { guestRepository.findById("guest-1") } returns sampleGuest()
                every { reservationRepository.save(any()) } answers { firstArg() }
                justRun { eventPublisher.publishEvent(any()) }

                val result = sut.createReservation(
                    propertyId = "prop-1",
                    roomTypeId = "rt-1",
                    checkIn = checkIn,
                    checkOut = checkOut,
                    numberOfGuests = 2,
                    guestId = "guest-1",
                    guestName = "홍길동",
                    guestPhone = "010-1234-5678",
                    guestEmail = "hong@test.com",
                    channelCode = "DIRECT"
                )

                result.status shouldBe ReservationStatus.PENDING
                result.channel.commissionRate shouldBe BigDecimal.ZERO
                result.nightCount shouldBe 2
                result.pricing.totalAmount shouldBe Money.won(200_000)
                verify(exactly = 2) { inventoryReservationPort.reserve("prop-1", "rt-1", any()) }
            }
        }

        `when`("OTA 채널 예약이면") {
            then("수수료가 적용된다") {
                clearAllMocks()
                every { roomTypeRepository.findById("rt-1") } returns sampleRoomType()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel()
                every { ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus("prop-1", "rt-1", RatePlanStatus.ACTIVE) } returns emptyList()
                justRun { inventoryReservationPort.reserve("prop-1", "rt-1", any()) }
                every { guestRepository.findById("guest-1") } returns sampleGuest()
                every { reservationRepository.save(any()) } answers { firstArg() }
                justRun { eventPublisher.publishEvent(any()) }

                val result = sut.createReservation(
                    propertyId = "prop-1",
                    roomTypeId = "rt-1",
                    checkIn = checkIn,
                    checkOut = checkOut,
                    numberOfGuests = 2,
                    guestId = "guest-1",
                    guestName = "홍길동",
                    guestPhone = "010-1234-5678",
                    guestEmail = null,
                    channelCode = "AGODA"
                )

                result.channel.commissionRate shouldBe BigDecimal("0.15")
                result.pricing.commissionAmount shouldBe Money.won(30_000)
                result.pricing.netAmount shouldBe Money.won(170_000)
            }
        }
    }

    // -- 예약 확정 --

    given("예약 확정 시") {
        `when`("PENDING 상태의 예약을 확정하면") {
            then("CONFIRMED 상태로 변경된다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-c1", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                )
                every { reservationRepository.findById("rsv-c1") } returns reservation
                every { reservationRepository.save(any()) } answers { firstArg() }

                val result = sut.confirmReservation("prop-1", "rsv-c1")
                result.status shouldBe ReservationStatus.CONFIRMED
            }
        }

        `when`("고객 예약의 결제가 아직 승인되지 않았으면") {
            then("PMS 확정이 거부된다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-customer-pending", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
                    memberId = "member-1"
                )
                every { reservationRepository.findById("rsv-customer-pending") } returns reservation
                every { reservationPaymentPort.findByReservationId("rsv-customer-pending") } returns
                    ReservationPaymentSnapshot(
                        id = "pay-1",
                        reservationId = "rsv-customer-pending",
                        memberId = "member-1",
                        orderId = "order-1",
                        amount = Money.won(200_000),
                        status = ReservationPaymentStatus.CONFIRM_REQUESTED,
                        paymentKey = "payment-key",
                        failReason = null
                    )

                val exception = shouldThrow<BusinessException> {
                    sut.confirmReservation("prop-1", "rsv-customer-pending")
                }

                exception.code shouldBe "PAYMENT_NOT_APPROVED"
                verify(exactly = 0) { reservationRepository.save(any()) }
            }
        }

        `when`("고객 예약의 결제가 승인되었으면") {
            then("PMS 확정으로 CONFIRMED 상태가 된다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-customer-approved", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
                    memberId = "member-1"
                )
                every { reservationRepository.findById("rsv-customer-approved") } returns reservation
                every { reservationPaymentPort.findByReservationId("rsv-customer-approved") } returns
                    ReservationPaymentSnapshot(
                        id = "pay-1",
                        reservationId = "rsv-customer-approved",
                        memberId = "member-1",
                        orderId = "order-1",
                        amount = Money.won(200_000),
                        status = ReservationPaymentStatus.APPROVED,
                        paymentKey = "payment-key",
                        failReason = null
                    )
                every { reservationRepository.save(any()) } answers { firstArg() }

                val result = sut.confirmReservation("prop-1", "rsv-customer-approved")

                result.status shouldBe ReservationStatus.CONFIRMED
            }
        }
    }

    // -- 예약 취소 --

    given("예약 취소 시") {
        `when`("CONFIRMED 상태의 예약을 취소하면") {
            then("CANCELLED 상태로 변경되고 재고가 복원된다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-c2", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                ).confirm()
                every { reservationRepository.findById("rsv-c2") } returns reservation
                every { reservationRepository.save(any()) } answers { firstArg() }
                justRun { inventoryReservationPort.release("prop-1", "rt-1", any()) }
                justRun { eventPublisher.publishEvent(any()) }

                val result = sut.cancelReservation("prop-1", "rsv-c2")

                result.status shouldBe ReservationStatus.CANCELLED
                verify(exactly = 2) { inventoryReservationPort.release("prop-1", "rt-1", any()) }
            }
        }

        `when`("CHECKED_IN 상태의 예약을 취소하면") {
            then("예외가 발생한다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-c3", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                ).confirm().checkIn("room-101")
                every { reservationRepository.findById("rsv-c3") } returns reservation

                shouldThrow<IllegalStateException> {
                    sut.cancelReservation("prop-1", "rsv-c3")
                }
            }
        }
    }

    // -- 예약 생성: 실패 --

    given("예약 생성 실패 시") {
        `when`("존재하지 않는 RoomType이면") {
            then("NotFoundException이 발생한다") {
                clearAllMocks()
                every { roomTypeRepository.findById("rt-999") } returns null

                shouldThrow<NotFoundException> {
                    sut.createReservation(
                        propertyId = "prop-1", roomTypeId = "rt-999",
                        checkIn = checkIn, checkOut = checkOut,
                        numberOfGuests = 2, guestId = "guest-1",
                        guestName = "홍길동", guestPhone = "010-1234-5678",
                        guestEmail = null, channelCode = "DIRECT"
                    )
                }
            }
        }

        `when`("재고 충돌이 발생하면") {
            then("ConflictException이 전파된다") {
                clearAllMocks()
                every { roomTypeRepository.findById("rt-1") } returns sampleRoomType()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "DIRECT") } returns directChannel()
                every { ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(any(), any(), any()) } returns emptyList()
                every { inventoryReservationPort.reserve("prop-1", "rt-1", any()) } throws
                        ConflictException("INVENTORY_CONFLICT", "재고 변경 충돌")

                shouldThrow<ConflictException> {
                    sut.createReservation(
                        propertyId = "prop-1", roomTypeId = "rt-1",
                        checkIn = checkIn, checkOut = checkOut,
                        numberOfGuests = 2, guestId = "guest-1",
                        guestName = "홍길동", guestPhone = "010-1234-5678",
                        guestEmail = null, channelCode = "DIRECT"
                    )
                }
            }
        }
    }

})
