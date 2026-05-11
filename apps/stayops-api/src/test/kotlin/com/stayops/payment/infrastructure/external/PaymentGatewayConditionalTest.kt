package com.stayops.payment.infrastructure.external

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

class PaymentGatewayConditionalTest : BehaviorSpec({

    given("결제 gateway 조건부 활성화") {
        `when`("TossPaymentsClient 설정을 확인하면") {
            val annotation = TossPaymentsClient::class.java.getAnnotation(ConditionalOnProperty::class.java)

            then("기본 gateway로 활성화된다") {
                annotation.name.toList() shouldContain "stayops.payment.gateway"
                annotation.havingValue shouldBe "toss"
                annotation.matchIfMissing shouldBe true
            }
        }

        `when`("LoadtestPaymentGateway 설정을 확인하면") {
            val annotation = LoadtestPaymentGateway::class.java.getAnnotation(ConditionalOnProperty::class.java)

            then("loadtest 설정에서만 활성화된다") {
                annotation.name.toList() shouldContain "stayops.payment.gateway"
                annotation.havingValue shouldBe "loadtest"
                annotation.matchIfMissing shouldBe false
            }
        }
    }
})
