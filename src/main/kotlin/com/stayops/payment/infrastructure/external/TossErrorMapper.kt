package com.stayops.payment.infrastructure.external

import com.stayops.payment.domain.service.PaymentGatewayException

object TossErrorMapper {

    private val ALREADY_PROCESSED = setOf(
        "ALREADY_PROCESSED_PAYMENT"
    )

    private val PAYMENT_DECLINED = setOf(
        "REJECT_CARD_PAYMENT",
        "INVALID_STOPPED_CARD",
        "EXCEED_MAX_DAILY_PAYMENT_COUNT",
        "NOT_ENOUGH_CARD_BALANCE",
        "RESTRICTED_CARD",
        "EXCEED_MAX_AMOUNT",
        "INVALID_CARD_EXPIRATION",
        "INVALID_CARD_NUMBER",
        "CARD_LIMIT_EXCEEDED",
        "BELOW_MINIMUM_AMOUNT"
    )

    private val PROVIDER_ERROR = setOf(
        "PROVIDER_ERROR",
        "FAILED_INTERNAL_SYSTEM_PROCESSING",
        "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING"
    )

    private val INVALID_REQUEST = setOf(
        "INVALID_REQUEST",
        "INVALID_PAYMENT_KEY",
        "INVALID_ORDER_ID",
        "INVALID_AMOUNT",
        "UNAUTHORIZED_KEY"
    )

    fun map(error: TossErrorResponse): PaymentGatewayException = when (error.code) {
        in ALREADY_PROCESSED -> PaymentGatewayException.AlreadyProcessed(error.code)
        in PAYMENT_DECLINED -> PaymentGatewayException.PaymentDeclined(error.code, error.message)
        in PROVIDER_ERROR -> PaymentGatewayException.ProviderError(error.code, error.message)
        in INVALID_REQUEST -> PaymentGatewayException.InvalidRequest(error.code, error.message)
        else -> PaymentGatewayException.UnknownError(error.code, error.message)
    }
}
