package com.stayops.reservation.domain.model

import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate

class ReservationTest : BehaviorSpec({

    val checkIn = LocalDate.of(2026, 4, 1)
    val checkOut = LocalDate.of(2026, 4, 3)
    val dateRange = DateRange.of(checkIn, checkOut)

    fun directChannel() = BookingChannel(
        channelCode = "DIRECT",
        commissionRate = BigDecimal.ZERO
    )

    fun otaChannel() = BookingChannel(
        channelCode = "AGODA",
        externalReservationId = "AGD-12345",
        commissionRate = BigDecimal("0.15")
    )

    fun guestInfo() = GuestInfo(name = "홍길동", phone = "010-1234-5678", email = "hong@test.com")

    fun directPricing() = ReservationPricing.calculate(
        roomRate = Money.won(200_000),
        additionalCharges = Money.ZERO,
        commissionRate = BigDecimal.ZERO
    )

    fun otaPricing() = ReservationPricing.calculate(
        roomRate = Money.won(200_000),
        additionalCharges = Money.ZERO,
        commissionRate = BigDecimal("0.15")
    )

    fun pendingReservation() = Reservation.create(
        id = "rsv-1",
        propertyId = "prop-1",
        roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = guestInfo(),
        dateRange = dateRange,
        numberOfGuests = 2,
        channel = directChannel(),
        pricing = directPricing()
    )

    // -- ReservationPricing 계산 --

    given("ReservationPricing 계산 시") {
        `when`("DIRECT 채널 (수수료 0%)이면") {
            val pricing = directPricing()

            then("commissionAmount가 0이다") {
                pricing.commissionAmount shouldBe Money.ZERO
            }
            then("netAmount가 totalAmount와 같다") {
                pricing.netAmount shouldBe pricing.totalAmount
            }
        }

        `when`("OTA 채널 (수수료 15%)이면") {
            val pricing = otaPricing()

            then("commissionAmount가 30000원이다") {
                pricing.commissionAmount shouldBe Money.won(30_000)
            }
            then("netAmount가 170000원이다") {
                pricing.netAmount shouldBe Money.won(170_000)
            }
            then("totalAmount가 200000원이다") {
                pricing.totalAmount shouldBe Money.won(200_000)
            }
        }
    }

    // -- 예약 생성 --

    given("예약 생성 시") {
        `when`("유효한 정보로 생성하면") {
            val reservation = pendingReservation()

            then("PENDING 상태로 생성된다") {
                reservation.status shouldBe ReservationStatus.PENDING
            }
            then("nightCount가 dateRange의 박수와 같다") {
                reservation.nightCount shouldBe 2
            }
            then("roomId가 null이다") {
                reservation.roomId shouldBe null
            }
            then("version이 0이다") {
                reservation.version shouldBe 0L
            }
        }

        `when`("인원이 0이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Reservation.create(
                        id = "rsv-2",
                        propertyId = "prop-1",
                        roomTypeId = "rt-1",
                        guestId = "guest-1",
                        guestInfo = guestInfo(),
                        dateRange = dateRange,
                        numberOfGuests = 0,
                        channel = directChannel(),
                        pricing = directPricing()
                    )
                }
            }
        }
    }

    // -- BookingChannel 불변식 --

    given("BookingChannel 생성 시") {
        `when`("channelCode가 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    BookingChannel(channelCode = "", commissionRate = BigDecimal.ZERO)
                }
            }
        }

        `when`("commissionRate이 음수이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    BookingChannel(channelCode = "AGODA", commissionRate = BigDecimal("-0.1"))
                }
            }
        }
    }

    // -- 상태 전이: PENDING → CONFIRMED --

    given("PENDING 상태의 예약") {
        val reservation = pendingReservation()

        `when`("confirm() 호출 시") {
            val confirmed = reservation.confirm()

            then("상태가 CONFIRMED로 변경된다") {
                confirmed.status shouldBe ReservationStatus.CONFIRMED
            }
        }

        `when`("checkIn() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    reservation.checkIn("room-101")
                }
            }
        }
    }

    // -- 상태 전이: CONFIRMED --

    given("CONFIRMED 상태의 예약") {
        val reservation = pendingReservation().confirm()

        `when`("checkIn() 호출 시") {
            val checkedIn = reservation.checkIn("room-101")

            then("상태가 CHECKED_IN으로 변경된다") {
                checkedIn.status shouldBe ReservationStatus.CHECKED_IN
            }
            then("roomId가 배정된다") {
                checkedIn.roomId shouldBe "room-101"
            }
        }

        `when`("cancel() 호출 시") {
            val cancelled = reservation.cancel()

            then("상태가 CANCELLED로 변경된다") {
                cancelled.status shouldBe ReservationStatus.CANCELLED
            }
        }

        `when`("noShow() 호출 시") {
            val noShow = reservation.noShow()

            then("상태가 NO_SHOW로 변경된다") {
                noShow.status shouldBe ReservationStatus.NO_SHOW
            }
        }
    }

    // -- 상태 전이: CHECKED_IN --

    given("CHECKED_IN 상태의 예약") {
        val reservation = pendingReservation().confirm().checkIn("room-101")

        `when`("checkOut() 호출 시") {
            val checkedOut = reservation.checkOut()

            then("상태가 CHECKED_OUT으로 변경된다") {
                checkedOut.status shouldBe ReservationStatus.CHECKED_OUT
            }
        }

        `when`("cancel() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    reservation.cancel()
                }
            }
        }
    }

    // -- 상태 전이: CHECKED_OUT (종료 상태) --

    given("CHECKED_OUT 상태의 예약") {
        val reservation = pendingReservation().confirm().checkIn("room-101").checkOut()

        `when`("cancel() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    reservation.cancel()
                }
            }
        }
    }

    // -- 상태 전이: CANCELLED (종료 상태) --

    given("CANCELLED 상태의 예약") {
        val reservation = pendingReservation().confirm().cancel()

        `when`("confirm() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    reservation.confirm()
                }
            }
        }
    }

    // -- GuestInfo 불변식 --

    given("GuestInfo 생성 시") {
        `when`("이름이 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    GuestInfo(name = "", phone = "010-1234-5678", email = null)
                }
            }
        }

        `when`("전화번호가 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    GuestInfo(name = "홍길동", phone = "", email = null)
                }
            }
        }
    }
})
