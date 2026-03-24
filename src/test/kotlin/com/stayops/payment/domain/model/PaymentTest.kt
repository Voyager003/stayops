package com.stayops.payment.domain.model

import com.stayops.shared.domain.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class PaymentTest : BehaviorSpec({

    fun pendingPayment() = Payment.create(
        id = "pay-1",
        reservationId = "rsv-1",
        memberId = "member-1",
        amount = Money.won(200_000)
    )

    // -- Payment 생성 --

    given("Payment 생성 시") {
        `when`("유효한 정보로 생성하면") {
            val payment = pendingPayment()

            then("PENDING 상태로 생성된다") {
                payment.status shouldBe PaymentStatus.PENDING
            }
            then("orderId가 생성된다") {
                payment.orderId shouldNotBe null
                payment.orderId.startsWith("STAYOPS-rsv-1-") shouldBe true
            }
            then("amount가 설정된다") {
                payment.amount shouldBe Money.won(200_000)
            }
            then("paymentKey는 null이다") {
                payment.paymentKey shouldBe null
            }
            then("method는 null이다") {
                payment.method shouldBe null
            }
            then("failReason은 null이다") {
                payment.failReason shouldBe null
            }
            then("approvedAt은 null이다") {
                payment.approvedAt shouldBe null
            }
            then("version이 0이다") {
                payment.version shouldBe 0L
            }
        }

        `when`("reservationId가 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Payment.create(
                        id = "pay-2",
                        reservationId = "",
                        memberId = "member-1",
                        amount = Money.won(100_000)
                    )
                }
            }
        }

        `when`("memberId가 빈 문자열이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    Payment.create(
                        id = "pay-3",
                        reservationId = "rsv-1",
                        memberId = "",
                        amount = Money.won(100_000)
                    )
                }
            }
        }
    }

    // -- 상태 전이: PENDING → APPROVED --

    given("PENDING 상태의 Payment") {
        val payment = pendingPayment()

        `when`("approve() 호출 시") {
            val approvedAt = Instant.parse("2026-04-01T12:00:00Z")
            val approved = payment.approve(
                paymentKey = "toss_pk_123",
                method = "카드",
                approvedAt = approvedAt
            )

            then("상태가 APPROVED로 변경된다") {
                approved.status shouldBe PaymentStatus.APPROVED
            }
            then("paymentKey가 설정된다") {
                approved.paymentKey shouldBe "toss_pk_123"
            }
            then("method가 설정된다") {
                approved.method shouldBe "카드"
            }
            then("approvedAt이 설정된다") {
                approved.approvedAt shouldBe approvedAt
            }
        }

        `when`("fail() 호출 시") {
            val failed = payment.fail("잔액 부족")

            then("상태가 FAILED로 변경된다") {
                failed.status shouldBe PaymentStatus.FAILED
            }
            then("failReason이 설정된다") {
                failed.failReason shouldBe "잔액 부족"
            }
        }

        `when`("cancel() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    payment.cancel()
                }
            }
        }

        `when`("failCancel() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    payment.failCancel("실패")
                }
            }
        }
    }

    // -- 상태 전이: APPROVED → CANCELLED --

    given("APPROVED 상태의 Payment") {
        val approvedAt = Instant.parse("2026-04-01T12:00:00Z")
        val payment = pendingPayment().approve(
            paymentKey = "toss_pk_123",
            method = "카드",
            approvedAt = approvedAt
        )

        `when`("cancel() 호출 시") {
            val cancelled = payment.cancel()

            then("상태가 CANCELLED로 변경된다") {
                cancelled.status shouldBe PaymentStatus.CANCELLED
            }
        }

        `when`("approve() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    payment.approve(
                        paymentKey = "toss_pk_456",
                        method = "계좌이체",
                        approvedAt = Instant.now()
                    )
                }
            }
        }

        `when`("fail() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    payment.fail("오류")
                }
            }
        }

        `when`("failCancel() 호출 시") {
            val cancelFailed = payment.failCancel("Toss 환불 API 실패")

            then("상태가 CANCEL_FAILED로 변경된다") {
                cancelFailed.status shouldBe PaymentStatus.CANCEL_FAILED
            }
            then("failReason이 설정된다") {
                cancelFailed.failReason shouldBe "Toss 환불 API 실패"
            }
        }
    }

    // -- 상태 전이: FAILED (종료 상태) --

    given("FAILED 상태의 Payment") {
        val payment = pendingPayment().fail("결제 실패")

        `when`("approve() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    payment.approve(
                        paymentKey = "toss_pk_789",
                        method = "카드",
                        approvedAt = Instant.now()
                    )
                }
            }
        }

        `when`("cancel() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    payment.cancel()
                }
            }
        }
    }

    // -- 상태 전이: CANCELLED (종료 상태) --

    given("CANCELLED 상태의 Payment") {
        val approvedAt = Instant.parse("2026-04-01T12:00:00Z")
        val payment = pendingPayment().approve(
            paymentKey = "toss_pk_123",
            method = "카드",
            approvedAt = approvedAt
        ).cancel()

        `when`("approve() 호출 시") {
            then("예외가 발생한다") {
                shouldThrow<IllegalStateException> {
                    payment.approve(
                        paymentKey = "toss_pk_456",
                        method = "계좌이체",
                        approvedAt = Instant.now()
                    )
                }
            }
        }
    }
})
