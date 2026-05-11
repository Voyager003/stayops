package com.stayops.payment.domain.service

sealed class PaymentGatewayException(message: String) : RuntimeException(message) {

    /** Already processed payment — idempotent, not a failure */
    class AlreadyProcessed(val paymentKey: String) :
        PaymentGatewayException("이미 처리된 결제입니다: $paymentKey")

    /** Card declined, insufficient balance, etc. — user's responsibility */
    class PaymentDeclined(val code: String, val reason: String) :
        PaymentGatewayException("결제가 거절되었습니다 [$code]: $reason")

    /** PG/card company system error — retryable */
    class ProviderError(val code: String, val reason: String) :
        PaymentGatewayException("PG사 시스템 오류 [$code]: $reason")

    /** Invalid request — our code bug */
    class InvalidRequest(val code: String, val reason: String) :
        PaymentGatewayException("잘못된 요청입니다 [$code]: $reason")

    /** Unclassified error */
    class UnknownError(val code: String, val reason: String) :
        PaymentGatewayException("알 수 없는 결제 오류 [$code]: $reason")
}
