package com.stayops.reservation.domain.model

import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ReservationIntentTest : BehaviorSpec({

    val now = Instant.parse("2026-04-01T01:00:00Z")
    val expiresAt = Instant.parse("2026-04-01T01:15:00Z")
    val dateRange = DateRange.of(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3))

    fun guestInfo() = GuestInfo(name = "홍길동", phone = "010-1234-5678", email = "hong@test.com")

    fun directChannel() = ReservationChannel(
        channelCode = "DIRECT",
        commissionRate = BigDecimal.ZERO
    )

    fun pricing() = ReservationPricing.calculate(
        roomRate = Money.won(200_000),
        additionalCharges = Money.ZERO,
        commissionRate = BigDecimal.ZERO
    )

    fun intent() = ReservationIntent.create(
        id = "intent-1",
        memberId = "member-1",
        propertyId = "property-1",
        roomTypeId = "room-type-1",
        guestInfo = guestInfo(),
        dateRange = dateRange,
        numberOfGuests = 2,
        channel = directChannel(),
        pricing = pricing(),
        paymentId = "payment-1",
        holdId = "hold-1",
        expiresAt = expiresAt,
        now = now
    )

    given("ReservationIntent 생성 시") {
        `when`("유효한 예약 입력과 결제 기준 정보가 있으면") {
            val created = intent()

            then("결제 대기 상태로 생성된다") {
                created.status shouldBe ReservationIntentStatus.PAYMENT_WAITING
            }
            then("아직 최종 예약 식별자는 없다") {
                created.reservationId shouldBe null
            }
            then("결제와 재고 hold 식별자를 가진다") {
                created.paymentId shouldBe "payment-1"
                created.holdId shouldBe "hold-1"
            }
            then("숙박 박수가 계산된다") {
                created.nightCount shouldBe 2
            }
        }

        `when`("투숙 인원이 0이면") {
            then("생성할 수 없다") {
                shouldThrow<IllegalArgumentException> {
                    ReservationIntent.create(
                        id = "intent-2",
                        memberId = "member-1",
                        propertyId = "property-1",
                        roomTypeId = "room-type-1",
                        guestInfo = guestInfo(),
                        dateRange = dateRange,
                        numberOfGuests = 0,
                        channel = directChannel(),
                        pricing = pricing(),
                        paymentId = "payment-1",
                        holdId = "hold-1",
                        expiresAt = expiresAt,
                        now = now
                    )
                }
            }
        }
    }

    given("결제 승인 요청을 시작할 때") {
        `when`("intent가 만료되지 않은 결제 대기 상태이면") {
            val requested = intent().requestPaymentConfirmation(now.plusSeconds(60))

            then("결제 승인 요청 상태가 된다") {
                requested.status shouldBe ReservationIntentStatus.CONFIRM_REQUESTED
            }
        }

        `when`("intent가 이미 만료되었으면") {
            then("결제 승인 요청을 시작할 수 없다") {
                shouldThrow<IllegalStateException> {
                    intent().requestPaymentConfirmation(expiresAt.plusSeconds(1))
                }
            }
        }
    }

    given("결제 승인 이후 최종 예약으로 전환할 때") {
        `when`("결제 승인 요청 상태이면") {
            val reserved = intent()
                .requestPaymentConfirmation(now.plusSeconds(60))
                .markReserved("reservation-1")

            then("최종 예약 식별자를 연결하고 예약 완료 상태가 된다") {
                reserved.status shouldBe ReservationIntentStatus.RESERVED
                reserved.reservationId shouldBe "reservation-1"
            }
        }

        `when`("결제 대기 상태이면") {
            then("바로 예약 완료로 전환할 수 없다") {
                shouldThrow<IllegalStateException> {
                    intent().markReserved("reservation-1")
                }
            }
        }
    }
})
