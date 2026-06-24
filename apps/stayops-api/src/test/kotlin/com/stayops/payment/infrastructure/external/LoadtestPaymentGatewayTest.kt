package com.stayops.payment.infrastructure.external

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import java.math.BigDecimal

class LoadtestPaymentGatewayTest : BehaviorSpec({

    given("loadtest 결제 gateway") {
        val gateway = LoadtestPaymentGateway(latencyMs = 0)

        `when`("결제 승인을 요청하면") {
            val result = gateway.confirm(
                paymentKey = "loadtest-payment-key-1",
                orderId = "STAYOPS-reservation-1-1",
                amount = BigDecimal("40000"),
                idempotencyKey = "payment-confirm:payment-1"
            )

            then("외부 PG 없이 승인 성공 결과를 반환한다") {
                result.paymentKey shouldBe "loadtest-payment-key-1"
                result.orderId shouldBe "STAYOPS-reservation-1-1"
                result.totalAmount shouldBe BigDecimal("40000")
                result.method shouldBe "LOADTEST"
                result.approvedAt.shouldNotBeNull()
            }
        }

        `when`("승인 상태를 조회하면") {
            val result = gateway.inquire("loadtest-payment-key-1")

            then("DONE 상태를 반환한다") {
                result.paymentKey shouldBe "loadtest-payment-key-1"
                result.status shouldBe "DONE"
            }
        }

        `when`("결제 취소를 요청하면") {
            val result = gateway.cancel(
                paymentKey = "loadtest-payment-key-1",
                cancelReason = "loadtest",
                idempotencyKey = "payment-cancel:payment-1"
            )

            then("취소 성공 결과를 반환한다") {
                result.paymentKey shouldBe "loadtest-payment-key-1"
            }
        }
    }
})
