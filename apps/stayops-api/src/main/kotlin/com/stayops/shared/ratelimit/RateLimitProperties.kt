package com.stayops.shared.ratelimit

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "stayops.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    val failOpen: Boolean = true,
    val rules: List<RateLimitRuleProperties> = defaultRules()
) {
    fun activeRules(): List<RateLimitRule> = rules.map { it.toRule() }

    companion object {
        fun defaultRules(): List<RateLimitRuleProperties> = listOf(
            RateLimitRuleProperties("auth-login", "POST", "/api/v1/auth/login", 10, 60, RateLimitIdentityType.IP),
            RateLimitRuleProperties("auth-signup", "POST", "/api/v1/auth/signup", 5, 600, RateLimitIdentityType.IP),
            RateLimitRuleProperties("customer-auth-login", "POST", "/api/v1/customer/auth/login", 10, 60, RateLimitIdentityType.IP),
            RateLimitRuleProperties("customer-auth-signup", "POST", "/api/v1/customer/auth/signup", 5, 600, RateLimitIdentityType.IP),
            RateLimitRuleProperties("customer-reservation-create", "POST", "/api/v1/customer/reservations", 5, 60, RateLimitIdentityType.MEMBER),
            RateLimitRuleProperties(
                "customer-reservation-payment-confirm",
                "POST",
                "/api/v1/customer/reservation-intents/{reservationIntentId}/confirm-payment",
                10,
                60,
                RateLimitIdentityType.MEMBER
            ),
            RateLimitRuleProperties(
                "customer-reservation-cancel",
                "POST",
                "/api/v1/customer/reservations/{reservationId}/cancel",
                10,
                60,
                RateLimitIdentityType.MEMBER
            ),
            RateLimitRuleProperties("payment-webhook", "POST", "/api/v1/payments/toss/webhooks/**", 120, 60, RateLimitIdentityType.IP),
            RateLimitRuleProperties("channel-webhook", "POST", "/api/v1/properties/{propertyId}/channels/webhook/**", 120, 60, RateLimitIdentityType.IP)
        )
    }
}

data class RateLimitRuleProperties(
    val id: String,
    val method: String,
    val pathPattern: String,
    val limit: Long,
    val windowSeconds: Long,
    val identityType: RateLimitIdentityType
) {
    fun toRule(): RateLimitRule = RateLimitRule(
        id = id,
        method = method,
        pathPattern = pathPattern,
        limit = limit,
        windowSeconds = windowSeconds,
        identityType = identityType
    )
}
