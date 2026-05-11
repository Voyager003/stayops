package com.stayops.payment.domain.service

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class PaymentGatewayExceptionTest : BehaviorSpec({

    given("PaymentGatewayException 계층") {

        `when`("AlreadyProcessed 생성 시") {
            val ex = PaymentGatewayException.AlreadyProcessed("toss_pk_123")

            then("paymentKey가 설정된다") {
                ex.paymentKey shouldBe "toss_pk_123"
            }
            then("PaymentGatewayException의 서브타입이다") {
                ex.shouldBeInstanceOf<PaymentGatewayException>()
            }
            then("message가 포함된다") {
                ex.message shouldBe "이미 처리된 결제입니다: toss_pk_123"
            }
        }

        `when`("PaymentDeclined 생성 시") {
            val ex = PaymentGatewayException.PaymentDeclined("REJECT_CARD_PAYMENT", "잔액이 부족합니다")

            then("code와 reason이 설정된다") {
                ex.code shouldBe "REJECT_CARD_PAYMENT"
                ex.reason shouldBe "잔액이 부족합니다"
            }
            then("PaymentGatewayException의 서브타입이다") {
                ex.shouldBeInstanceOf<PaymentGatewayException>()
            }
        }

        `when`("ProviderError 생성 시") {
            val ex = PaymentGatewayException.ProviderError("PROVIDER_ERROR", "PG사 시스템 오류")

            then("code와 reason이 설정된다") {
                ex.code shouldBe "PROVIDER_ERROR"
                ex.reason shouldBe "PG사 시스템 오류"
            }
            then("PaymentGatewayException의 서브타입이다") {
                ex.shouldBeInstanceOf<PaymentGatewayException>()
            }
        }

        `when`("InvalidRequest 생성 시") {
            val ex = PaymentGatewayException.InvalidRequest("INVALID_REQUEST", "잘못된 요청입니다")

            then("code와 reason이 설정된다") {
                ex.code shouldBe "INVALID_REQUEST"
                ex.reason shouldBe "잘못된 요청입니다"
            }
            then("PaymentGatewayException의 서브타입이다") {
                ex.shouldBeInstanceOf<PaymentGatewayException>()
            }
        }

        `when`("UnknownError 생성 시") {
            val ex = PaymentGatewayException.UnknownError("UNKNOWN_CODE", "알 수 없는 오류")

            then("code와 reason이 설정된다") {
                ex.code shouldBe "UNKNOWN_CODE"
                ex.reason shouldBe "알 수 없는 오류"
            }
            then("PaymentGatewayException의 서브타입이다") {
                ex.shouldBeInstanceOf<PaymentGatewayException>()
            }
        }
    }
})
