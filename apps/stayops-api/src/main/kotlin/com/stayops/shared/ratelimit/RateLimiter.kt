package com.stayops.shared.ratelimit

interface RateLimiter {
    fun check(rule: RateLimitRule, identity: String): RateLimitResult
}

data class RateLimitResult(
    val allowed: Boolean,
    val retryAfterSeconds: Long
) {
    companion object {
        fun allowed(): RateLimitResult = RateLimitResult(allowed = true, retryAfterSeconds = 0)

        fun blocked(retryAfterSeconds: Long): RateLimitResult =
            RateLimitResult(allowed = false, retryAfterSeconds = retryAfterSeconds.coerceAtLeast(1))
    }
}
