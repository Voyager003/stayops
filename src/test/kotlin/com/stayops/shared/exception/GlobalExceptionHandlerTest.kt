package com.stayops.shared.exception

import com.stayops.payment.domain.service.PaymentGatewayException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GlobalExceptionHandlerTest : BehaviorSpec({

    val handler = GlobalExceptionHandler()

    given("BusinessException 발생 시") {
        val exception = BusinessException("INVALID_DATE_RANGE", "체크아웃은 체크인보다 이후여야 합니다.")

        `when`("핸들러가 처리하면") {
            val response = handler.handleBusinessException(exception)

            then("HTTP 400을 반환한다") {
                response.statusCode.value() shouldBe 400
            }
            then("에러 코드와 메시지가 포함된다") {
                response.body!!.code shouldBe "INVALID_DATE_RANGE"
                response.body!!.message shouldBe "체크아웃은 체크인보다 이후여야 합니다."
            }
            then("timestamp가 포함된다") {
                response.body!!.timestamp shouldNotBe null
            }
        }
    }

    given("NotFoundException 발생 시") {
        val exception = NotFoundException("PROPERTY_NOT_FOUND", "숙소를 찾을 수 없습니다.")

        `when`("핸들러가 처리하면") {
            val response = handler.handleNotFoundException(exception)

            then("HTTP 404를 반환한다") {
                response.statusCode.value() shouldBe 404
            }
            then("에러 코드와 메시지가 포함된다") {
                response.body!!.code shouldBe "PROPERTY_NOT_FOUND"
                response.body!!.message shouldBe "숙소를 찾을 수 없습니다."
            }
        }
    }

    given("ConflictException 발생 시") {
        val exception = ConflictException("INVENTORY_CONFLICT", "재고 충돌이 발생했습니다. 다시 시도해 주세요.")

        `when`("핸들러가 처리하면") {
            val response = handler.handleConflictException(exception)

            then("HTTP 409를 반환한다") {
                response.statusCode.value() shouldBe 409
            }
            then("에러 코드와 메시지가 포함된다") {
                response.body!!.code shouldBe "INVENTORY_CONFLICT"
                response.body!!.message shouldBe "재고 충돌이 발생했습니다. 다시 시도해 주세요."
            }
        }
    }

    // -- PaymentGatewayException --

    given("PaymentGatewayException.AlreadyProcessed 발생 시") {
        val exception = PaymentGatewayException.AlreadyProcessed("toss_pk_123")

        `when`("핸들러가 처리하면") {
            val response = handler.handlePaymentGatewayException(exception)

            then("HTTP 200을 반환한다") {
                response.statusCode.value() shouldBe 200
            }
            then("코드가 ALREADY_PROCESSED이다") {
                response.body!!.code shouldBe "ALREADY_PROCESSED"
            }
        }
    }

    given("PaymentGatewayException.PaymentDeclined 발생 시") {
        val exception = PaymentGatewayException.PaymentDeclined("REJECT_CARD_PAYMENT", "잔액 부족")

        `when`("핸들러가 처리하면") {
            val response = handler.handlePaymentGatewayException(exception)

            then("HTTP 400을 반환한다") {
                response.statusCode.value() shouldBe 400
            }
            then("코드가 PAYMENT_DECLINED이다") {
                response.body!!.code shouldBe "PAYMENT_DECLINED"
            }
        }
    }

    given("PaymentGatewayException.ProviderError 발생 시") {
        val exception = PaymentGatewayException.ProviderError("PROVIDER_ERROR", "PG사 장애")

        `when`("핸들러가 처리하면") {
            val response = handler.handlePaymentGatewayException(exception)

            then("HTTP 502를 반환한다") {
                response.statusCode.value() shouldBe 502
            }
            then("코드가 PROVIDER_ERROR이다") {
                response.body!!.code shouldBe "PROVIDER_ERROR"
            }
        }
    }

    given("PaymentGatewayException.InvalidRequest 발생 시") {
        val exception = PaymentGatewayException.InvalidRequest("INVALID_REQUEST", "잘못된 요청")

        `when`("핸들러가 처리하면") {
            val response = handler.handlePaymentGatewayException(exception)

            then("HTTP 500을 반환한다") {
                response.statusCode.value() shouldBe 500
            }
            then("코드가 PAYMENT_INVALID_REQUEST이다") {
                response.body!!.code shouldBe "PAYMENT_INVALID_REQUEST"
            }
        }
    }

    given("PaymentGatewayException.UnknownError 발생 시") {
        val exception = PaymentGatewayException.UnknownError("SOME_CODE", "알 수 없는 오류")

        `when`("핸들러가 처리하면") {
            val response = handler.handlePaymentGatewayException(exception)

            then("HTTP 500을 반환한다") {
                response.statusCode.value() shouldBe 500
            }
            then("코드가 PAYMENT_UNKNOWN_ERROR이다") {
                response.body!!.code shouldBe "PAYMENT_UNKNOWN_ERROR"
            }
        }
    }
})
