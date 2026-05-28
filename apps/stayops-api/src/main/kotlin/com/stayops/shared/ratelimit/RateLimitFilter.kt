package com.stayops.shared.ratelimit

import com.stayops.member.domain.model.Member
import com.stayops.shared.exception.ErrorResponse
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
class RateLimitFilter(
    private val rateLimiter: RateLimiter,
    private val properties: RateLimitProperties,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val activeRules = properties.activeRules()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!properties.enabled) {
            filterChain.doFilter(request, response)
            return
        }

        val rule = activeRules.firstOrNull { it.matches(request.method, request.requestURI) }
        if (rule == null) {
            filterChain.doFilter(request, response)
            return
        }

        val identity = identityFor(rule, request)
        val result = try {
            rateLimiter.check(rule, identity)
        } catch (ex: RuntimeException) {
            meterRegistry.counter(METRIC_FALLBACK, "rule", rule.id).increment()
            log.warn("Rate limiter failed open: rule={}, message={}", rule.id, ex.message)
            if (properties.failOpen) {
                filterChain.doFilter(request, response)
                return
            }
            throw ex
        }

        if (result.allowed) {
            meterRegistry.counter(METRIC_ALLOWED, "rule", rule.id).increment()
            filterChain.doFilter(request, response)
            return
        }

        meterRegistry.counter(METRIC_BLOCKED, "rule", rule.id).increment()
        writeTooManyRequests(response, result.retryAfterSeconds)
    }

    private fun identityFor(rule: RateLimitRule, request: HttpServletRequest): String =
        when (rule.identityType) {
            RateLimitIdentityType.IP -> "ip:${clientIp(request)}"
            RateLimitIdentityType.MEMBER -> {
                val member = SecurityContextHolder.getContext().authentication?.principal as? Member
                if (member != null) "member:${member.id}" else "ip:${clientIp(request)}"
            }
        }

    private fun clientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.sanitizeIdentityPart()
        if (!forwarded.isNullOrBlank()) {
            return forwarded
        }
        return request.remoteAddr.sanitizeIdentityPart().ifBlank { "unknown" }
    }

    private fun String.sanitizeIdentityPart(): String =
        trim()
            .replace(CONTROL_CHARACTERS, "_")
            .take(MAX_IDENTITY_LENGTH)

    private fun writeTooManyRequests(response: HttpServletResponse, retryAfterSeconds: Long) {
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
        objectMapper.writeValue(
            response.writer,
            ErrorResponse(
                code = "RATE_LIMIT_EXCEEDED",
                message = "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                timestamp = Instant.now()
            )
        )
    }

    companion object {
        private const val METRIC_ALLOWED = "stayops.rate_limit.allowed"
        private const val METRIC_BLOCKED = "stayops.rate_limit.blocked"
        private const val METRIC_FALLBACK = "stayops.rate_limit.fallback"
        private const val MAX_IDENTITY_LENGTH = 128
        private val CONTROL_CHARACTERS = Regex("[\\r\\n\\t]")
    }
}
