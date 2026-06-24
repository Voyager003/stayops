package com.stayops.payment.application.required

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.reflect.full.memberProperties

class PaymentGatewayContractTest : BehaviorSpec({

    given("결제 승인 결과 계약을 정의할 때") {
        `when`("PaymentConfirmResult의 공개 프로퍼티를 확인하면") {
            val propertyNames = PaymentConfirmResult::class.memberProperties.map { it.name }.sorted()

            then("핵심 승인 정보만 노출한다") {
                propertyNames shouldContainExactly listOf(
                    "approvedAt",
                    "method",
                    "orderId",
                    "paymentKey",
                    "totalAmount"
                )
            }
        }

        `when`("결제 승인 결과를 생성하면") {
            val result = PaymentConfirmResult(
                paymentKey = "pay-key",
                orderId = "order-1",
                method = "카드",
                approvedAt = null,
                totalAmount = java.math.BigDecimal("1000")
            )

            then("승인 흐름에 필요한 정보만 담는다") {
                result.paymentKey shouldBe "pay-key"
                result.orderId shouldBe "order-1"
                result.method shouldBe "카드"
                result.approvedAt shouldBe null
                result.totalAmount shouldBe java.math.BigDecimal("1000")
            }
        }
    }
})
