package com.stayops.reservation.application.service

import com.stayops.inventory.application.provided.InventoryReservationService
import com.stayops.reservation.application.required.ReservationPaymentSnapshot
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.PagedResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class CustomerReservationApplicationTest : BehaviorSpec({

    val intentCreationApplication = mockk<CustomerReservationIntentCreationApplication>()
    val intentPaymentApplication = mockk<CustomerReservationIntentPaymentApplication>()
    val reservationRepository = mockk<ReservationRepository>()
    val reservationPaymentService = mockk<com.stayops.reservation.application.required.ReservationPaymentService>()
    val inventoryReservationService = mockk<InventoryReservationService>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val queryApplication = CustomerReservationQueryApplication(
        reservationRepository = reservationRepository,
        reservationPaymentPort = reservationPaymentService
    )
    val cancellationApplication = CustomerReservationCancellationApplication(
        reservationRepository = reservationRepository,
        reservationPaymentPort = reservationPaymentService,
        inventoryReservationService = inventoryReservationService,
        eventPublisher = eventPublisher
    )
    val service = CustomerReservationApplication(
        intentCreationApplication = intentCreationApplication,
        intentPaymentApplication = intentPaymentApplication,
        queryApplication = queryApplication,
        cancellationApplication = cancellationApplication
    )

    val checkIn = LocalDate.of(2026, 4, 1)
    val checkOut = LocalDate.of(2026, 4, 3)
    val now = Instant.parse("2026-04-08T10:00:00Z")

    fun reservation(memberId: String = "member-1") = Reservation.create(
        id = "rsv-1",
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = GuestInfo("김고객", "010-1111-2222", null),
        dateRange = DateRange.of(checkIn, checkOut),
        numberOfGuests = 2,
        channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
        memberId = memberId
    )

    fun payment(
        status: ReservationPaymentStatus = ReservationPaymentStatus.PENDING,
        reservationId: String? = "rsv-1",
        reservationIntentId: String? = null
    ) = ReservationPaymentSnapshot(
        id = "pay-1",
        reservationId = reservationId,
        reservationIntentId = reservationIntentId,
        memberId = "member-1",
        orderId = "STAYOPS-test",
        amount = Money.won(200_000),
        status = status,
        paymentKey = null,
        failReason = null
    )

    fun intentResult() = CustomerReservationIntentResult(
        intent = ReservationIntent.create(
            id = "intent-1",
            memberId = "member-1",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            guestInfo = GuestInfo("김고객", "010-1111-2222", "kim@test.com"),
            dateRange = DateRange.of(checkIn, checkOut),
            numberOfGuests = 2,
            channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
            pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
            paymentId = "pay-1",
            holdId = "hold-1",
            expiresAt = now.plusSeconds(900),
            now = now
        ),
        payment = payment(
            status = ReservationPaymentStatus.PENDING,
            reservationId = null,
            reservationIntentId = "intent-1"
        )
    )

    given("예약 intent 생성 시") {
        `when`("고객 예약 생성을 요청하면") {
            clearAllMocks()
            every {
                intentCreationApplication.createReservationIntent(
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
            } returns intentResult()

            val result = service.createReservationIntent(
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

            then("Reservation이 아니라 ReservationIntent 결과를 반환한다") {
                result.intent.id shouldBe "intent-1"
                result.payment.reservationId shouldBe null
                result.payment.reservationIntentId shouldBe "intent-1"
            }
        }
    }

    given("예약 intent 결제 승인 요청 시") {
        `when`("고객 결제 승인을 요청하면") {
            clearAllMocks()
            every {
                intentPaymentApplication.confirmPayment(
                    memberId = "member-1",
                    reservationIntentId = "intent-1",
                    paymentKey = "toss_pk_123",
                    orderId = "STAYOPS-test",
                    amount = BigDecimal(200_000)
                )
            } returns intentResult().let {
                CustomerReservationIntentResult(
                    it.intent.requestPaymentConfirmation(now),
                    it.payment.copy(status = ReservationPaymentStatus.CONFIRM_REQUESTED, paymentKey = "toss_pk_123")
                )
            }

            val result = service.confirmReservationIntentPayment(
                memberId = "member-1",
                reservationIntentId = "intent-1",
                paymentKey = "toss_pk_123",
                orderId = "STAYOPS-test",
                amount = BigDecimal(200_000)
            )

            then("reservationId 기반 결제가 아니라 intent 기반 결제 결과를 반환한다") {
                result.intent.id shouldBe "intent-1"
                result.payment.status shouldBe ReservationPaymentStatus.CONFIRM_REQUESTED
                result.payment.paymentKey shouldBe "toss_pk_123"
            }
        }

        `when`("고객이 예약 intent 결제 상태를 조회하면") {
            clearAllMocks()
            every {
                intentPaymentApplication.getPaymentStatus(
                    memberId = "member-1",
                    reservationIntentId = "intent-1"
                )
            } returns intentResult()

            val result = service.getReservationIntentPaymentStatus(
                memberId = "member-1",
                reservationIntentId = "intent-1"
            )

            then("intent 기준 결제 상태를 반환한다") {
                result.intent.id shouldBe "intent-1"
                result.payment.reservationIntentId shouldBe "intent-1"
            }
        }
    }

    given("내 예약 조회 시") {
        `when`("예약 목록을 조회하면") {
            clearAllMocks()
            val reservation = reservation()
            every { reservationRepository.findPageByMemberId("member-1", 0, 20) } returns PagedResult(
                content = listOf(reservation),
                totalElements = 1,
                page = 0,
                size = 20,
                totalPages = 1
            )
            every { reservationPaymentService.findByReservationIds(listOf("rsv-1")) } returns
                listOf(payment(status = ReservationPaymentStatus.APPROVED))

            val result = service.getMyReservations("member-1", page = 0, size = 20)

            then("예약과 결제 상태를 함께 반환한다") {
                result.content.size shouldBe 1
                result.content[0].reservation.id shouldBe "rsv-1"
                result.content[0].payment?.status shouldBe ReservationPaymentStatus.APPROVED
                result.totalElements shouldBe 1
                verify(exactly = 0) { reservationPaymentService.findByMemberId(any()) }
            }
        }

        `when`("예약 상세를 조회하면") {
            clearAllMocks()
            every { reservationRepository.findById("rsv-1") } returns reservation()
            every { reservationPaymentService.findByReservationId("rsv-1") } returns
                payment(status = ReservationPaymentStatus.APPROVED)

            val result = service.getMyReservation("member-1", "rsv-1")

            then("예약과 결제 상태를 함께 반환한다") {
                result.reservation.id shouldBe "rsv-1"
                result.payment?.status shouldBe ReservationPaymentStatus.APPROVED
            }
        }
    }

    given("예약 취소 시") {
        `when`("PENDING 예약을 취소하면") {
            clearAllMocks()
            val pendingReservation = reservation()
            val pendingPayment = payment()
            every { reservationRepository.findById("rsv-1") } returns pendingReservation
            every { reservationPaymentService.findByReservationId("rsv-1") } returns pendingPayment
            every { reservationRepository.save(any()) } answers { firstArg() }
            every { reservationPaymentService.cancelPendingByCustomerRequest("rsv-1") } returns
                pendingPayment.copy(status = ReservationPaymentStatus.FAILED, failReason = "고객 요청에 의한 취소")
            justRun { inventoryReservationService.release(any(), any(), any()) }

            val result = service.cancelReservation(memberId = "member-1", reservationId = "rsv-1")

            then("예약 취소와 PENDING 결제 실패만 처리하고 재고 복원은 하지 않는다") {
                result.reservation.status shouldBe ReservationStatus.CANCELLED
                result.payment.status shouldBe ReservationPaymentStatus.FAILED
                verify(exactly = 1) { reservationPaymentService.cancelPendingByCustomerRequest("rsv-1") }
                verify(exactly = 0) { inventoryReservationService.release(any(), any(), any()) }
            }
        }

        `when`("CONFIRMED 예약을 취소하면") {
            clearAllMocks()
            val confirmedReservation = reservation().confirm()
            val approvedPayment = payment(status = ReservationPaymentStatus.APPROVED)
            every { reservationRepository.findById("rsv-1") } returns confirmedReservation
            every { reservationPaymentService.findByReservationId("rsv-1") } returns approvedPayment
            every { reservationRepository.save(any()) } answers { firstArg() }
            every { reservationPaymentService.requestCancelByCustomerRequest("rsv-1", "member-1") } returns
                approvedPayment.copy(status = ReservationPaymentStatus.CANCEL_REQUESTED)
            justRun { inventoryReservationService.release(any(), any(), any()) }

            val result = service.cancelReservation(memberId = "member-1", reservationId = "rsv-1")

            then("예약 취소와 결제 취소 요청을 만들고 숙박일만큼 재고를 복원한다") {
                result.reservation.status shouldBe ReservationStatus.CANCELLED
                result.payment.status shouldBe ReservationPaymentStatus.CANCEL_REQUESTED
                verify(exactly = 1) { reservationPaymentService.requestCancelByCustomerRequest("rsv-1", "member-1") }
                verify(exactly = 2) { inventoryReservationService.release("prop-1", "rt-1", any()) }
            }
        }

        `when`("다른 사용자의 예약을 취소하면") {
            clearAllMocks()
            every { reservationRepository.findById("rsv-1") } returns reservation(memberId = "member-1")

            then("접근이 거부된다") {
                shouldThrow<com.stayops.shared.exception.ForbiddenException> {
                    service.cancelReservation(memberId = "member-999", reservationId = "rsv-1")
                }
            }
        }
    }
})
