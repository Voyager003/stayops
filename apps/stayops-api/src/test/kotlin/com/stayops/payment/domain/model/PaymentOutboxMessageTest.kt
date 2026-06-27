package com.stayops.payment.domain.model

import com.stayops.shared.domain.Money
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class PaymentOutboxMessageTest : BehaviorSpec({

    val now = Instant.parse("2026-04-13T10:00:00Z")

    fun confirmMessage() = PaymentOutboxMessage.createConfirm(
        id = "outbox-1",
        paymentId = "pay-1",
        reservationId = "rsv-1",
        memberId = "member-1",
        paymentKey = "toss_pk_123",
        orderId = "STAYOPS-rsv-1-123",
        amount = Money.won(200_000),
        now = now
    )

    fun cancelMessage() = PaymentOutboxMessage.createCancel(
        id = "outbox-2",
        paymentId = "pay-1",
        reservationId = "rsv-1",
        memberId = "member-1",
        paymentKey = "toss_pk_123",
        orderId = "STAYOPS-rsv-1-123",
        amount = Money.won(200_000),
        cancelReason = "고객 요청에 의한 취소",
        now = now
    )

    given("결제 승인 Outbox 메시지 생성 시") {
        `when`("유효한 값으로 생성하면") {
            val message = confirmMessage()

            then("PENDING 상태와 고정 멱등성 키를 가진다") {
                message.type shouldBe PaymentOutboxType.CONFIRM_PAYMENT
                message.status shouldBe PaymentOutboxStatus.PENDING
                message.retryCount shouldBe 0
                message.idempotencyKey shouldBe "payment-confirm:pay-1:STAYOPS-rsv-1-123"
                message.lockedBy shouldBe null
                message.lockedUntil shouldBe null
            }
        }

        `when`("paymentKey가 공백이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    PaymentOutboxMessage.createConfirm(
                        id = "outbox-1",
                        paymentId = "pay-1",
                        reservationId = "rsv-1",
                        memberId = "member-1",
                        paymentKey = "",
                        orderId = "STAYOPS-rsv-1-123",
                        amount = Money.won(200_000),
                        now = now
                    )
                }
            }
        }
    }

    given("결제 취소 Outbox 메시지 생성 시") {
        `when`("유효한 값으로 생성하면") {
            val message = cancelMessage()

            then("PENDING 상태와 고정 멱등성 키와 취소 사유를 가진다") {
                message.type shouldBe PaymentOutboxType.CANCEL_PAYMENT
                message.status shouldBe PaymentOutboxStatus.PENDING
                message.retryCount shouldBe 0
                message.idempotencyKey shouldBe "payment-cancel:pay-1:STAYOPS-rsv-1-123"
                message.cancelReason shouldBe "고객 요청에 의한 취소"
            }
        }

        `when`("cancelReason이 공백이면") {
            then("예외가 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    PaymentOutboxMessage.createCancel(
                        id = "outbox-2",
                        paymentId = "pay-1",
                        reservationId = "rsv-1",
                        memberId = "member-1",
                        paymentKey = "toss_pk_123",
                        orderId = "STAYOPS-rsv-1-123",
                        amount = Money.won(200_000),
                        cancelReason = "",
                        now = now
                    )
                }
            }
        }
    }

    given("PaymentOutboxMessage 동일성 비교 시") {
        `when`("같은 id의 Outbox 메시지가 서로 다른 처리 상태를 가지면") {
            val pending = confirmMessage()
            val processing = pending.startProcessing("worker-1", now.plusSeconds(60), now)

            then("같은 메시지로 판단한다") {
                processing shouldBe pending
                processing.hashCode() shouldBe pending.hashCode()
            }
        }

        `when`("id가 다르면 같은 결제 작업이라도") {
            val first = confirmMessage()
            val second = PaymentOutboxMessage.createConfirm(
                id = "outbox-99",
                paymentId = first.paymentId,
                reservationId = first.reservationId!!,
                memberId = first.memberId,
                paymentKey = first.paymentKey,
                orderId = first.orderId,
                amount = first.amount,
                now = now
            )

            then("다른 메시지로 판단한다") {
                second shouldNotBe first
            }
        }

        `when`("HashSet에 같은 id의 다른 상태 메시지를 넣으면") {
            val messages = hashSetOf(confirmMessage())
            messages += confirmMessage().startProcessing("worker-1", now.plusSeconds(60), now)

            then("중복 추가되지 않는다") {
                messages.size shouldBe 1
            }
        }
    }

    given("Outbox 메시지 처리 상태 전이 시") {
        `when`("PENDING 메시지 처리를 시작하면") {
            val processing = confirmMessage().startProcessing(
                workerId = "worker-1",
                lockedUntil = now.plusSeconds(60),
                now = now
            )

            then("IN_PROGRESS 상태와 lease를 가진다") {
                processing.status shouldBe PaymentOutboxStatus.IN_PROGRESS
                processing.lockedBy shouldBe "worker-1"
                processing.lockedUntil shouldBe now.plusSeconds(60)
            }
        }

        `when`("IN_PROGRESS 메시지를 완료하면") {
            val completed = confirmMessage()
                .startProcessing("worker-1", now.plusSeconds(60), now)
                .complete(now.plusSeconds(5))

            then("COMPLETED 상태가 되고 lease가 해제된다") {
                completed.status shouldBe PaymentOutboxStatus.COMPLETED
                completed.lockedBy shouldBe null
                completed.lockedUntil shouldBe null
                completed.nextRetryAt shouldBe null
            }
        }

        `when`("처리 실패 후 재시도 가능 횟수가 남아 있으면") {
            val failed = confirmMessage()
                .startProcessing("worker-1", now.plusSeconds(60), now)
                .fail("PG timeout", now.plusSeconds(5))

            then("PENDING으로 돌아가고 다음 재시도 시간이 생긴다") {
                failed.status shouldBe PaymentOutboxStatus.PENDING
                failed.retryCount shouldBe 1
                failed.nextRetryAt shouldNotBe null
                failed.lockedBy shouldBe null
                failed.lockedUntil shouldBe null
                failed.lastError shouldBe "PG timeout"
            }
        }

        `when`("최대 재시도 횟수에 도달하면") {
            val failed = confirmMessage()
                .startProcessing("worker-1", now.plusSeconds(60), now)
                .fail("PG timeout", now.plusSeconds(5))
                .startProcessing("worker-1", now.plusSeconds(120), now.plusSeconds(40))
                .fail("PG timeout", now.plusSeconds(45))
                .startProcessing("worker-1", now.plusSeconds(180), now.plusSeconds(120))
                .fail("PG timeout", now.plusSeconds(125))

            then("FAILED 상태가 된다") {
                failed.status shouldBe PaymentOutboxStatus.FAILED
                failed.retryCount shouldBe 3
                failed.nextRetryAt shouldBe null
            }
        }

        `when`("처리 중 서버가 죽어서 lease가 만료되면") {
            val stuck = confirmMessage().startProcessing(
                workerId = "worker-1",
                lockedUntil = now.plusSeconds(60),
                now = now
            )

            val reclaimed = stuck.startProcessing(
                workerId = "worker-2",
                lockedUntil = now.plusSeconds(180),
                now = now.plusSeconds(61)
            )

            then("다른 worker가 다시 처리할 수 있다") {
                reclaimed.status shouldBe PaymentOutboxStatus.IN_PROGRESS
                reclaimed.lockedBy shouldBe "worker-2"
                reclaimed.lockedUntil shouldBe now.plusSeconds(180)
            }
        }
    }
})
