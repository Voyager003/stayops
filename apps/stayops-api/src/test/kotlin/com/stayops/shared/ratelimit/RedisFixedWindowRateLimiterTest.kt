package com.stayops.shared.ratelimit

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RedisFixedWindowRateLimiterTest {

    private val redisTemplate = mockk<RedisTemplate<String, String>>()

    @Test
    fun should_block_after_fixed_window_limit_is_exceeded() {
        every {
            redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), any<String>())
        } returnsMany listOf(1L, 2L, 3L)
        val limiter = RedisFixedWindowRateLimiter(
            redisTemplate = redisTemplate,
            clock = Clock.fixed(Instant.parse("2026-05-28T00:00:30Z"), ZoneOffset.UTC)
        )

        assertThat(limiter.check(rule(limit = 2), "ip:203.0.113.10").allowed).isTrue()
        assertThat(limiter.check(rule(limit = 2), "ip:203.0.113.10").allowed).isTrue()

        val blocked = limiter.check(rule(limit = 2), "ip:203.0.113.10")

        assertThat(blocked.allowed).isFalse()
        assertThat(blocked.retryAfterSeconds).isEqualTo(30)
    }

    @Test
    fun should_hash_identity_in_redis_key() {
        val capturedKeys = mutableListOf<List<String>>()
        every {
            redisTemplate.execute(any<RedisScript<Long>>(), capture(capturedKeys), any<String>())
        } returns 1L
        val limiter = RedisFixedWindowRateLimiter(
            redisTemplate = redisTemplate,
            clock = Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneOffset.UTC)
        )

        limiter.check(rule(limit = 2), "ip:203.0.113.10")

        assertThat(capturedKeys).hasSize(1)
        assertThat(capturedKeys.first().first()).startsWith("rate:auth-login:")
        assertThat(capturedKeys.first().first()).doesNotContain("203.0.113.10")
    }

    private fun rule(limit: Long): RateLimitRule = RateLimitRule(
        id = "auth-login",
        method = "POST",
        pathPattern = "/api/v1/auth/login",
        limit = limit,
        windowSeconds = 60,
        identityType = RateLimitIdentityType.IP
    )
}
