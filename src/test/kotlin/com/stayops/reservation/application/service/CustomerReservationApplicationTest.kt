package com.stayops.reservation.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.model.PaymentOutboxStatus
import com.stayops.payment.domain.model.PaymentOutboxType
import com.stayops.payment.domain.model.PaymentStatus
import com.stayops.payment.domain.repository.PaymentOutboxRepository
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.reservation.domain.model.*
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.ForbiddenException
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CustomerReservationApplicationTest : BehaviorSpec({

    val propertyRepository = mockk<PropertyRepository>()
    val roomTypeRepository = mockk<RoomTypeRepository>()
    val guestRepository = mockk<GuestRepository>()
    val channelRepository = mockk<ChannelRepository>()
    val ratePlanRepository = mockk<RatePlanRepository>()
    val reservationRepository = mockk<ReservationRepository>()
    val paymentRepository = mockk<PaymentRepository>()
    val paymentOutboxRepository = mockk<PaymentOutboxRepository>()
    val inventoryReservationPort = mockk<InventoryReservationPort>()
    val paymentGateway = mockk<PaymentGateway>()
    val rateResolverService = RateResolverService()
    val fixedInstant = Instant.parse("2026-04-08T10:00:00Z")
    val clock = Clock.fixed(fixedInstant, ZoneId.of("Asia/Seoul"))
    val idGenerator = object : IdGenerator {
        override fun generate(): String = "test-id"
    }

    val service = CustomerReservationApplication(
        propertyRepository = propertyRepository,
        roomTypeRepository = roomTypeRepository,
        guestRepository = guestRepository,
        channelRepository = channelRepository,
        ratePlanRepository = ratePlanRepository,
        reservationRepository = reservationRepository,
        paymentRepository = paymentRepository,
        paymentOutboxRepository = paymentOutboxRepository,
        inventoryReservationPort = inventoryReservationPort,
        rateResolverService = rateResolverService,
        clock = clock,
        idGenerator = idGenerator
    )

    val checkIn = LocalDate.of(2026, 4, 1)
    val checkOut = LocalDate.of(2026, 4, 3)

    fun activeProperty() = Property.create(
        id = "prop-1", ownerId = "owner-1", name = "테스트 호텔",
        type = PropertyType.HOTEL,
        address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
        contactInfo = ContactInfo.of("02-1234-5678", "hotel@test.com"),
        description = "테스트 호텔", timezone = "Asia/Seoul", currency = "KRW"
    ).activate()

    fun activeRoomType() = RoomType.create(
        id = "rt-1", propertyId = "prop-1", name = "디럭스룸",
        description = "넓은 객실", maxOccupancy = 2,
        basePrice = Money.won(100_000)
    )

    fun directChannel() = Channel.createDirect(id = "ch-1", propertyId = "prop-1")

    fun existingGuest() = Guest.create(
        id = "guest-1", propertyId = "prop-1",
        name = "김고객", phone = "010-1111-2222", email = "kim@test.com"
    )

    fun setupCommonMocks() {
        every { reservationRepository.existsByMemberIdAndRoomTypeIdAndCheckInAndCheckOutAndStatusIn(any(), any(), any(), any(), any()) } returns false
        every { propertyRepository.findById("prop-1") } returns activeProperty()
        every { roomTypeRepository.findById("rt-1") } returns activeRoomType()
        every { channelRepository.findByPropertyIdAndCode("prop-1", "DIRECT") } returns directChannel()
        every { ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus("prop-1", "rt-1", RatePlanStatus.ACTIVE) } returns emptyList()
        justRun { inventoryReservationPort.reserve(any(), any(), any()) }
        every { reservationRepository.save(any()) } answers { firstArg() }
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { paymentOutboxRepository.save(any()) } answers { firstArg() }
    }

    fun pendingReservation(memberId: String = "member-1") = Reservation.create(
        id = "rsv-1", propertyId = "prop-1", roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = GuestInfo("김고객", "010-1111-2222", null),
        dateRange = DateRange.of(checkIn, checkOut),
        numberOfGuests = 2,
        channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
        memberId = memberId
    )

    fun expiredReservation(memberId: String = "member-1") = Reservation.create(
        id = "rsv-1", propertyId = "prop-1", roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = GuestInfo("김고객", "010-1111-2222", null),
        dateRange = DateRange.of(checkIn, checkOut),
        numberOfGuests = 2,
        channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
        memberId = memberId,
        expiresAt = fixedInstant.minusSeconds(60)  // fixed clock 기준 1분 전 만료
    )

    fun pendingPayment(reservationId: String = "rsv-1", memberId: String = "member-1") = Payment.create(
        id = "pay-1", reservationId = reservationId, memberId = memberId,
        amount = Money.won(200_000)
    )

    // -- 예약 생성 --

    given("예약 생성 시") {

        `when`("신규 고객이 예약하면") {
            clearAllMocks()
            setupCommonMocks()
            every { guestRepository.findByPropertyIdAndPhone("prop-1", "010-1111-2222") } returns null
            every { guestRepository.save(any()) } answers { firstArg() }

            val result = service.createReservation(
                memberId = "member-1", propertyId = "prop-1", roomTypeId = "rt-1",
                checkIn = checkIn, checkOut = checkOut, numberOfGuests = 2,
                guestName = "김고객", guestPhone = "010-1111-2222", guestEmail = "kim@test.com"
            )

            then("PENDING 예약 + 결제가 생성되고 Guest 생성 + 재고 차감") {
                result.reservation.status shouldBe ReservationStatus.PENDING
                result.reservation.memberId shouldBe "member-1"
                result.reservation.expiresAt shouldNotBe null
                result.payment.status shouldBe PaymentStatus.PENDING
                result.payment.memberId shouldBe "member-1"
                verify { guestRepository.save(any()) }
                verify(exactly = 2) { inventoryReservationPort.reserve("prop-1", "rt-1", any()) }
            }
        }

        `when`("기존 고객이 예약하면") {
            clearAllMocks()
            setupCommonMocks()
            every { guestRepository.findByPropertyIdAndPhone("prop-1", "010-1111-2222") } returns existingGuest()

            val result = service.createReservation(
                memberId = "member-1", propertyId = "prop-1", roomTypeId = "rt-1",
                checkIn = checkIn, checkOut = checkOut, numberOfGuests = 2,
                guestName = "김고객", guestPhone = "010-1111-2222", guestEmail = "kim@test.com"
            )

            then("기존 Guest를 재사용한다") {
                result.reservation.guestId shouldBe "guest-1"
                verify(exactly = 0) { guestRepository.save(any()) }
            }
        }

        `when`("INACTIVE 숙소에 예약하면") {
            clearAllMocks()
            val inactive = Property.create(
                id = "prop-2", ownerId = "owner-1", name = "비활성",
                type = PropertyType.HOTEL,
                address = Address.of("서울", "서울", "서울", "00000", "KR"),
                contactInfo = ContactInfo.of("02-0000-0000", "x@test.com"),
                description = "비활성", timezone = "Asia/Seoul", currency = "KRW"
            )
            every { propertyRepository.findById("prop-2") } returns inactive
            every { reservationRepository.existsByMemberIdAndRoomTypeIdAndCheckInAndCheckOutAndStatusIn(any(), any(), any(), any(), any()) } returns false

            then("예외가 발생한다") {
                shouldThrow<BusinessException> {
                    service.createReservation(
                        memberId = "member-1", propertyId = "prop-2", roomTypeId = "rt-1",
                        checkIn = checkIn, checkOut = checkOut, numberOfGuests = 2,
                        guestName = "김고객", guestPhone = "010-1111-2222", guestEmail = null
                    )
                }
            }
        }

        `when`("동일 조건의 PENDING/CONFIRMED 예약이 이미 존재하면") {
            clearAllMocks()
            every { reservationRepository.existsByMemberIdAndRoomTypeIdAndCheckInAndCheckOutAndStatusIn(
                "member-1", "rt-1", checkIn, checkOut,
                listOf(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)
            ) } returns true

            then("ConflictException이 발생하고 재고 차감이 호출되지 않는다") {
                val ex = shouldThrow<ConflictException> {
                    service.createReservation(
                        memberId = "member-1", propertyId = "prop-1", roomTypeId = "rt-1",
                        checkIn = checkIn, checkOut = checkOut, numberOfGuests = 2,
                        guestName = "김고객", guestPhone = "010-1111-2222", guestEmail = null
                    )
                }
                ex.code shouldBe "DUPLICATE_RESERVATION"
                verify(exactly = 0) { inventoryReservationPort.reserve(any(), any(), any()) }
                verify(exactly = 0) { reservationRepository.save(any()) }
            }
        }
    }

    // -- 결제 확인 --

    given("결제 확인 시") {

        `when`("정상적으로 결제 승인 요청을 접수하면") {
            clearAllMocks()
            val reservation = pendingReservation()
            val payment = pendingPayment()
            every { reservationRepository.findById("rsv-1") } returns reservation
            every { paymentRepository.findByReservationId("rsv-1") } returns payment
            every { paymentOutboxRepository.findByPaymentIdAndType("pay-1", PaymentOutboxType.CONFIRM_PAYMENT) } returns null
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { paymentOutboxRepository.save(any()) } answers { firstArg() }

            val result = service.confirmPayment(
                memberId = "member-1", reservationId = "rsv-1",
                paymentKey = "toss_pk_123", orderId = payment.orderId,
                amount = BigDecimal(200_000)
            )

            then("Payment CONFIRM_REQUESTED + Reservation PENDING + Outbox PENDING") {
                result.payment.status shouldBe PaymentStatus.CONFIRM_REQUESTED
                result.payment.paymentKey shouldBe "toss_pk_123"
                result.reservation.status shouldBe ReservationStatus.PENDING
                verify { paymentRepository.save(match { it.status == PaymentStatus.CONFIRM_REQUESTED }) }
                verify {
                    paymentOutboxRepository.save(match {
                        it.status == PaymentOutboxStatus.PENDING &&
                            it.type == PaymentOutboxType.CONFIRM_PAYMENT &&
                            it.idempotencyKey == "payment-confirm:pay-1:${payment.orderId}"
                    })
                }
                verify(exactly = 0) { paymentGateway.confirm(any(), any(), any(), any()) }
            }
        }

        `when`("이미 결제 승인 Outbox가 생성된 요청을 다시 보내면") {
            clearAllMocks()
            val reservation = pendingReservation()
            val payment = pendingPayment().requestConfirm("toss_pk_123")
            val existingOutbox = com.stayops.payment.domain.model.PaymentOutboxMessage.createConfirm(
                id = "outbox-1",
                paymentId = "pay-1",
                reservationId = "rsv-1",
                memberId = "member-1",
                paymentKey = "toss_pk_123",
                orderId = payment.orderId,
                amount = Money.won(200_000),
                now = fixedInstant
            )
            every { reservationRepository.findById("rsv-1") } returns reservation
            every { paymentRepository.findByReservationId("rsv-1") } returns payment
            every { paymentOutboxRepository.findByPaymentIdAndType("pay-1", PaymentOutboxType.CONFIRM_PAYMENT) } returns existingOutbox

            val result = service.confirmPayment(
                memberId = "member-1", reservationId = "rsv-1",
                paymentKey = "toss_pk_123", orderId = payment.orderId,
                amount = BigDecimal(200_000)
            )

            then("Outbox를 중복 생성하지 않고 기존 요청 상태를 반환한다") {
                result.payment.status shouldBe PaymentStatus.CONFIRM_REQUESTED
                result.reservation.status shouldBe ReservationStatus.PENDING
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
                verify(exactly = 0) { paymentGateway.confirm(any(), any(), any(), any()) }
            }
        }

        `when`("만료된 PENDING 예약에 결제하면") {
            clearAllMocks()
            val reservation = expiredReservation()
            val payment = pendingPayment()
            every { reservationRepository.findById("rsv-1") } returns reservation
            every { paymentRepository.findByReservationId("rsv-1") } returns payment

            then("예외가 발생하고 Toss 승인이 호출되지 않는다") {
                shouldThrow<BusinessException> {
                    service.confirmPayment(
                        memberId = "member-1", reservationId = "rsv-1",
                        paymentKey = "toss_pk_123", orderId = payment.orderId,
                        amount = BigDecimal(200_000)
                    )
                }
                verify(exactly = 0) { paymentGateway.confirm(any(), any(), any(), any()) }
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
            }
        }

        `when`("클라이언트가 보낸 금액이 DB 금액과 다르면") {
            clearAllMocks()
            val reservation = pendingReservation()
            val payment = pendingPayment()  // amount = 200,000원
            every { reservationRepository.findById("rsv-1") } returns reservation
            every { paymentRepository.findByReservationId("rsv-1") } returns payment

            then("예외가 발생하고 Toss 승인이 호출되지 않는다") {
                shouldThrow<BusinessException> {
                    service.confirmPayment(
                        memberId = "member-1", reservationId = "rsv-1",
                        paymentKey = "toss_pk_123", orderId = payment.orderId,
                        amount = BigDecimal(1_000)  // 200,000원을 1,000원으로 조작
                    )
                }
                verify(exactly = 0) { paymentGateway.confirm(any(), any(), any(), any()) }
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
            }
        }

        `when`("클라이언트가 보낸 orderId가 DB orderId와 다르면") {
            clearAllMocks()
            val reservation = pendingReservation()
            val payment = pendingPayment()
            every { reservationRepository.findById("rsv-1") } returns reservation
            every { paymentRepository.findByReservationId("rsv-1") } returns payment

            then("예외가 발생하고 Toss 승인이 호출되지 않는다") {
                shouldThrow<BusinessException> {
                    service.confirmPayment(
                        memberId = "member-1", reservationId = "rsv-1",
                        paymentKey = "toss_pk_123", orderId = "TAMPERED-ORDER-ID",
                        amount = BigDecimal(200_000)
                    )
                }
                verify(exactly = 0) { paymentGateway.confirm(any(), any(), any(), any()) }
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
            }
        }

        `when`("다른 사용자의 예약에 결제하면") {
            clearAllMocks()
            every { reservationRepository.findById("rsv-1") } returns pendingReservation(memberId = "member-1")

            then("Forbidden 예외가 발생한다") {
                shouldThrow<ForbiddenException> {
                    service.confirmPayment(
                        memberId = "member-999", reservationId = "rsv-1",
                        paymentKey = "pk", orderId = "oid", amount = BigDecimal(200_000)
                    )
                }
            }
        }

        `when`("이미 CONFIRMED 상태인 예약에 결제를 다시 요청하면") {
            clearAllMocks()
            val confirmedReservation = pendingReservation().confirm()
            val approvedPayment = pendingPayment().approve(
                paymentKey = "toss_pk_123", method = "카드", approvedAt = Instant.now()
            )
            every { reservationRepository.findById("rsv-1") } returns confirmedReservation
            every { paymentRepository.findByReservationId("rsv-1") } returns approvedPayment

            val result = service.confirmPayment(
                memberId = "member-1", reservationId = "rsv-1",
                paymentKey = "toss_pk_123", orderId = approvedPayment.orderId,
                amount = BigDecimal(200_000)
            )

            then("Toss 승인 없이 기존 결과를 반환한다 (멱등성)") {
                result.reservation.status shouldBe ReservationStatus.CONFIRMED
                result.payment.status shouldBe PaymentStatus.APPROVED
                result.payment.paymentKey shouldBe "toss_pk_123"
                verify(exactly = 0) { paymentGateway.confirm(any(), any(), any(), any()) }
            }
        }
    }

    // -- 예약 취소 --

    given("예약 취소 시") {

        `when`("PENDING 예약을 취소하면") {
            clearAllMocks()
            val reservation = pendingReservation()
            val payment = pendingPayment()
            every { reservationRepository.findById("rsv-1") } returns reservation
            every { paymentRepository.findByReservationId("rsv-1") } returns payment
            every { reservationRepository.save(any()) } answers { firstArg() }
            every { paymentRepository.save(any()) } answers { firstArg() }
            justRun { inventoryReservationPort.release(any(), any(), any()) }

            val result = service.cancelReservation(memberId = "member-1", reservationId = "rsv-1")

            then("Toss 환불 없이 예약 취소 + 재고 복원") {
                result.reservation.status shouldBe ReservationStatus.CANCELLED
                result.payment.status shouldBe PaymentStatus.FAILED
                result.payment.failReason shouldBe "고객 요청에 의한 취소"
                verify(exactly = 0) { paymentGateway.cancel(any(), any(), any()) }
                verify(exactly = 2) { inventoryReservationPort.release("prop-1", "rt-1", any()) }
            }
        }

        `when`("CONFIRMED 예약을 취소하면") {
            clearAllMocks()
            val reservation = pendingReservation().confirm()
            val payment = pendingPayment().approve(
                paymentKey = "toss_pk_456", method = "카드", approvedAt = Instant.now()
            )
            every { reservationRepository.findById("rsv-1") } returns reservation
            every { paymentRepository.findByReservationId("rsv-1") } returns payment
            every { paymentOutboxRepository.findByPaymentIdAndType("pay-1", PaymentOutboxType.CANCEL_PAYMENT) } returns null
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { reservationRepository.save(any()) } answers { firstArg() }
            every { paymentOutboxRepository.save(any()) } answers { firstArg() }
            justRun { inventoryReservationPort.release(any(), any(), any()) }

            val result = service.cancelReservation(memberId = "member-1", reservationId = "rsv-1")

            then("예약 취소 + 결제 취소 요청 + Outbox 생성 + 재고 복원") {
                result.reservation.status shouldBe ReservationStatus.CANCELLED
                result.payment.status shouldBe PaymentStatus.CANCEL_REQUESTED
                verify(exactly = 2) { inventoryReservationPort.release("prop-1", "rt-1", any()) }
                verify(exactly = 0) { paymentGateway.cancel(any(), any(), any()) }
                verify {
                    paymentOutboxRepository.save(match {
                        it.status == PaymentOutboxStatus.PENDING &&
                            it.type == PaymentOutboxType.CANCEL_PAYMENT &&
                            it.idempotencyKey == "payment-cancel:pay-1:${payment.orderId}"
                    })
                }
            }
        }

        `when`("CONFIRMED 예약 취소 요청 Outbox가 이미 있으면") {
            clearAllMocks()
            val reservation = pendingReservation().confirm()
            val payment = pendingPayment().approve(
                paymentKey = "toss_pk_789", method = "카드", approvedAt = Instant.now()
            ).requestCancel()
            val existingOutbox = com.stayops.payment.domain.model.PaymentOutboxMessage.createCancel(
                id = "outbox-cancel-1",
                paymentId = "pay-1",
                reservationId = "rsv-1",
                memberId = "member-1",
                paymentKey = "toss_pk_789",
                orderId = payment.orderId,
                amount = Money.won(200_000),
                cancelReason = "고객 요청에 의한 취소",
                now = fixedInstant
            )
            every { reservationRepository.findById("rsv-1") } returns reservation
            every { paymentRepository.findByReservationId("rsv-1") } returns payment
            every { paymentOutboxRepository.findByPaymentIdAndType("pay-1", PaymentOutboxType.CANCEL_PAYMENT) } returns existingOutbox
            every { reservationRepository.save(any()) } answers { firstArg() }
            justRun { inventoryReservationPort.release(any(), any(), any()) }

            val result = service.cancelReservation(memberId = "member-1", reservationId = "rsv-1")

            then("Outbox를 중복 생성하지 않고 결제 취소 요청 상태를 반환한다") {
                result.reservation.status shouldBe ReservationStatus.CANCELLED
                result.payment.status shouldBe PaymentStatus.CANCEL_REQUESTED
                verify(exactly = 0) { paymentOutboxRepository.save(any()) }
                verify(exactly = 0) { paymentGateway.cancel(any(), any(), any()) }
                verify(exactly = 2) { inventoryReservationPort.release("prop-1", "rt-1", any()) }
            }
        }
    }
})
