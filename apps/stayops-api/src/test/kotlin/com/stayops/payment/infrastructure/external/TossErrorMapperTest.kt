package com.stayops.payment.infrastructure.external

import com.stayops.payment.application.required.PaymentGatewayException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class TossErrorMapperTest : BehaviorSpec({

    given("Toss 에러 코드를 도메인 예외로 변환할 때") {

        // -- AlreadyProcessed --

        `when`("ALREADY_PROCESSED_PAYMENT 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("ALREADY_PROCESSED_PAYMENT", "이미 처리된 결제입니다"))

            then("AlreadyProcessed 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.AlreadyProcessed>()
            }
        }

        // -- PaymentDeclined --

        `when`("REJECT_CARD_PAYMENT 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("REJECT_CARD_PAYMENT", "카드가 거절되었습니다"))

            then("PaymentDeclined 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.PaymentDeclined>()
            }
        }

        `when`("INVALID_STOPPED_CARD 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("INVALID_STOPPED_CARD", "정지된 카드입니다"))

            then("PaymentDeclined 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.PaymentDeclined>()
            }
        }

        `when`("EXCEED_MAX_DAILY_PAYMENT_COUNT 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("EXCEED_MAX_DAILY_PAYMENT_COUNT", "일일 결제 한도 초과"))

            then("PaymentDeclined 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.PaymentDeclined>()
            }
        }

        `when`("NOT_ENOUGH_CARD_BALANCE 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("NOT_ENOUGH_CARD_BALANCE", "잔액 부족"))

            then("PaymentDeclined 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.PaymentDeclined>()
            }
        }

        `when`("RESTRICTED_CARD 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("RESTRICTED_CARD", "사용 제한 카드"))

            then("PaymentDeclined 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.PaymentDeclined>()
            }
        }

        // -- ProviderError --

        `when`("PROVIDER_ERROR 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("PROVIDER_ERROR", "PG사 오류"))

            then("ProviderError 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.ProviderError>()
            }
        }

        `when`("FAILED_INTERNAL_SYSTEM_PROCESSING 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("FAILED_INTERNAL_SYSTEM_PROCESSING", "내부 시스템 오류"))

            then("ProviderError 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.ProviderError>()
            }
        }

        `when`("FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING", "결제 내부 시스템 오류"))

            then("ProviderError 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.ProviderError>()
            }
        }

        // -- InvalidRequest --

        `when`("INVALID_REQUEST 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("INVALID_REQUEST", "잘못된 요청"))

            then("InvalidRequest 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.InvalidRequest>()
            }
        }

        `when`("INVALID_PAYMENT_KEY 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("INVALID_PAYMENT_KEY", "유효하지 않은 paymentKey"))

            then("InvalidRequest 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.InvalidRequest>()
            }
        }

        `when`("INVALID_ORDER_ID 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("INVALID_ORDER_ID", "유효하지 않은 orderId"))

            then("InvalidRequest 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.InvalidRequest>()
            }
        }

        // -- UnknownError --

        `when`("분류되지 않은 에러 코드이면") {
            val result = TossErrorMapper.map(TossErrorResponse("SOME_UNKNOWN_CODE", "알 수 없는 오류"))

            then("UnknownError 예외로 변환한다") {
                result.shouldBeInstanceOf<PaymentGatewayException.UnknownError>()
            }
        }
    }
})
