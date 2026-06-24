package com.stayops.reservation.application.service

import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.application.required.ReservationPaymentSnapshot
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.LocalDate

class ReservationCancellationPolicyTest : BehaviorSpec({

    val reservationPaymentPort = mockk<ReservationPaymentService>()
    val policy = ReservationCancellationPolicy(reservationPaymentPort)

    fun reservation(
        id: String = "rsv-1",
        memberId: String? = null
    ): Reservation = Reservation.create(
        id = id,
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = GuestInfo("홍길동", "010-1234-5678", null),
        dateRange = DateRange.of(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3)),
        numberOfGuests = 2,
        channel = ReservationChannel("DIRECT", null, BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
        memberId = memberId
    )

    fun payment(status: ReservationPaymentStatus): ReservationPaymentSnapshot =
        ReservationPaymentSnapshot(
            id = "pay-1",
            reservationId = "rsv-1",
            memberId = "member-1",
            orderId = "order-1",
            amount = Money.won(200_000),
            status = status,
            paymentKey = "payment-key",
            failReason = null
        )

    given("예약 취소 재고 복원 정책은") {
        `when`("확정된 예약이면") {
            then("결제 조회 없이 재고 복원 대상으로 판단한다") {
                clearMocks(reservationPaymentPort)

                val shouldRelease = policy.shouldReleaseInventoryOnCancel(reservation().confirm())

                shouldRelease shouldBe true
                verify(exactly = 0) { reservationPaymentPort.findByReservationId(any()) }
            }
        }

        `when`("PMS에서 생성된 PENDING 예약이면") {
            then("결제 조회 없이 재고 복원 대상으로 판단한다") {
                clearMocks(reservationPaymentPort)

                val shouldRelease = policy.shouldReleaseInventoryOnCancel(reservation())

                shouldRelease shouldBe true
                verify(exactly = 0) { reservationPaymentPort.findByReservationId(any()) }
            }
        }

        `when`("고객 예약인데 결제 정보가 없으면") {
            then("재고 복원 대상으로 판단하지 않는다") {
                clearMocks(reservationPaymentPort)
                every { reservationPaymentPort.findByReservationId("rsv-1") } returns null

                val shouldRelease = policy.shouldReleaseInventoryOnCancel(reservation(memberId = "member-1"))

                shouldRelease shouldBe false
            }
        }

        `when`("고객 예약 결제가 승인되었으면") {
            then("재고 복원 대상으로 판단한다") {
                clearMocks(reservationPaymentPort)
                every { reservationPaymentPort.findByReservationId("rsv-1") } returns payment(ReservationPaymentStatus.APPROVED)

                val shouldRelease = policy.shouldReleaseInventoryOnCancel(reservation(memberId = "member-1"))

                shouldRelease shouldBe true
            }
        }

        `when`("고객 예약 결제가 승인 요청 중이면") {
            then("아직 재고 복원 대상으로 판단하지 않는다") {
                clearMocks(reservationPaymentPort)
                every { reservationPaymentPort.findByReservationId("rsv-1") } returns
                    payment(ReservationPaymentStatus.CONFIRM_REQUESTED)

                val shouldRelease = policy.shouldReleaseInventoryOnCancel(reservation(memberId = "member-1"))

                shouldRelease shouldBe false
            }
        }

        `when`("고객 예약 결제 취소 요청이 이미 생성되었으면") {
            then("재고 복원 대상으로 판단한다") {
                clearMocks(reservationPaymentPort)
                every { reservationPaymentPort.findByReservationId("rsv-1") } returns
                    payment(ReservationPaymentStatus.CANCEL_REQUESTED)

                val shouldRelease = policy.shouldReleaseInventoryOnCancel(reservation(memberId = "member-1"))

                shouldRelease shouldBe true
            }
        }

        `when`("고객 예약 결제 취소 요청이 실패했으면") {
            then("재고 복원 대상으로 판단한다") {
                clearMocks(reservationPaymentPort)
                every { reservationPaymentPort.findByReservationId("rsv-1") } returns
                    payment(ReservationPaymentStatus.CANCEL_FAILED)

                val shouldRelease = policy.shouldReleaseInventoryOnCancel(reservation(memberId = "member-1"))

                shouldRelease shouldBe true
            }
        }
    }
})
