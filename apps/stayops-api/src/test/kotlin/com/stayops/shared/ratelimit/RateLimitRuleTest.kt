package com.stayops.shared.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RateLimitRuleTest {

    @Test
    fun should_match_rule_by_http_method_and_path_pattern() {
        val rule = RateLimitRule(
            id = "customer-reservation-cancel",
            method = "POST",
            pathPattern = "/api/v1/customer/reservations/{reservationId}/cancel",
            limit = 10,
            windowSeconds = 60,
            identityType = RateLimitIdentityType.MEMBER
        )

        assertThat(rule.matches("POST", "/api/v1/customer/reservations/res-1/cancel")).isTrue()
        assertThat(rule.matches("GET", "/api/v1/customer/reservations/res-1/cancel")).isFalse()
        assertThat(rule.matches("POST", "/api/v1/customer/reservations/res-1/confirm-payment")).isFalse()
    }

    @Test
    fun should_match_wildcard_suffix_pattern() {
        val rule = RateLimitRule(
            id = "payment-webhook",
            method = "POST",
            pathPattern = "/api/v1/payments/toss/webhooks/**",
            limit = 120,
            windowSeconds = 60,
            identityType = RateLimitIdentityType.IP
        )

        assertThat(rule.matches("POST", "/api/v1/payments/toss/webhooks")).isTrue()
        assertThat(rule.matches("POST", "/api/v1/payments/toss/webhooks/toss")).isTrue()
        assertThat(rule.matches("GET", "/api/v1/payments/toss/webhooks/toss")).isFalse()
    }

    @Test
    fun should_match_customer_reservation_intent_payment_confirm_default_rule() {
        val rule = RateLimitProperties.defaultRules()
            .first { it.id == "customer-reservation-payment-confirm" }
            .toRule()

        assertThat(rule.matches("POST", "/api/v1/customer/reservation-intents/intent-1/confirm-payment")).isTrue()
        assertThat(rule.matches("POST", "/api/v1/customer/reservations/rsv-1/confirm-payment")).isFalse()
    }

    @Test
    fun should_match_customer_reservation_intent_create_default_rule() {
        val rule = RateLimitProperties.defaultRules()
            .first { it.id == "customer-reservation-intent-create" }
            .toRule()

        assertThat(rule.matches("POST", "/api/v1/customer/reservation-intents")).isTrue()
        assertThat(rule.matches("POST", "/api/v1/customer/reservations")).isFalse()
    }
}
