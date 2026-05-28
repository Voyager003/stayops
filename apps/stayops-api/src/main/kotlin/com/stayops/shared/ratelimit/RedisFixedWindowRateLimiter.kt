package com.stayops.shared.ratelimit

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Clock
import java.util.HexFormat

@Component
class RedisFixedWindowRateLimiter(
    private val redisTemplate: RedisTemplate<String, String>,
    private val clock: Clock
) : RateLimiter {

    private val script = DefaultRedisScript(
        """
        local current = redis.call('INCR', KEYS[1])
        if current == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """.trimIndent(),
        Long::class.java
    )

    override fun check(rule: RateLimitRule, identity: String): RateLimitResult {
        val nowEpochSecond = clock.instant().epochSecond
        val windowStart = nowEpochSecond / rule.windowSeconds * rule.windowSeconds
        val key = key(rule, identity, windowStart)
        val count = redisTemplate.execute(script, listOf(key), rule.windowSeconds.toString()) ?: 1L

        return if (count <= rule.limit) {
            RateLimitResult.allowed()
        } else {
            RateLimitResult.blocked(retryAfterSeconds = windowStart + rule.windowSeconds - nowEpochSecond)
        }
    }

    private fun key(rule: RateLimitRule, identity: String, windowStart: Long): String =
        "rate:${rule.id}:${identity.sha256()}:$windowStart"

    private fun String.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
