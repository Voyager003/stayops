package com.stayops.shared.ratelimit

import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.databind.ObjectMapper
import java.time.Instant

class RateLimitFilterTest {

    private val rateLimiter = mockk<RateLimiter>()
    private val properties = RateLimitProperties(
        rules = listOf(
            RateLimitRuleProperties(
                id = "customer-reservation-create",
                method = "POST",
                pathPattern = "/api/v1/customer/reservations",
                limit = 1,
                windowSeconds = 60,
                identityType = RateLimitIdentityType.MEMBER
            )
        )
    )
    private val filter = RateLimitFilter(rateLimiter, properties, ObjectMapper(), SimpleMeterRegistry())

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun should_continue_when_request_does_not_match_any_rate_limit_rule() {
        val request = MockHttpServletRequest("GET", "/actuator/health")
        val response = MockHttpServletResponse()
        var called = false
        val chain = FilterChain { _, _ -> called = true }

        filter.doFilter(request, response, chain)

        assertThat(called).isTrue()
        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun should_block_with_429_when_rate_limit_is_exceeded() {
        val member = Member.create(
            id = "member-1",
            email = "member@test.com",
            passwordHash = "hash",
            name = "member",
            role = MemberRole.CUSTOMER
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(member, null, emptyList())
        every { rateLimiter.check(properties.rules[0].toRule(), "member:member-1") } returns RateLimitResult.blocked(42)

        val request = MockHttpServletRequest("POST", "/api/v1/customer/reservations")
        val response = MockHttpServletResponse()
        var called = false
        val chain = FilterChain { _, _ -> called = true }

        filter.doFilter(request, response, chain)

        assertThat(called).isFalse()
        assertThat(response.status).isEqualTo(429)
        assertThat(response.getHeader("Retry-After")).isEqualTo("42")
        assertThat(response.contentAsString).contains("RATE_LIMIT_EXCEEDED")
        verify { rateLimiter.check(properties.rules[0].toRule(), "member:member-1") }
    }

    @Test
    fun should_fail_open_when_redis_rate_limiter_fails() {
        every { rateLimiter.check(any(), any()) } throws IllegalStateException("redis down")

        val request = MockHttpServletRequest("POST", "/api/v1/customer/reservations")
        request.remoteAddr = "10.0.0.10"
        val response = MockHttpServletResponse()
        var called = false
        val chain = FilterChain { _, _ -> called = true }

        filter.doFilter(request, response, chain)

        assertThat(called).isTrue()
        assertThat(response.status).isEqualTo(200)
    }

    @Test
    fun should_use_first_forwarded_ip_for_ip_identity() {
        val ipRuleProperties = RateLimitRuleProperties(
            id = "auth-login",
            method = "POST",
            pathPattern = "/api/v1/auth/login",
            limit = 10,
            windowSeconds = 60,
            identityType = RateLimitIdentityType.IP
        )
        val ipProperties = RateLimitProperties(rules = listOf(ipRuleProperties))
        val ipFilter = RateLimitFilter(rateLimiter, ipProperties, ObjectMapper(), SimpleMeterRegistry())
        every { rateLimiter.check(ipRuleProperties.toRule(), "ip:203.0.113.10") } returns RateLimitResult.allowed()

        val request = MockHttpServletRequest("POST", "/api/v1/auth/login")
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
        request.remoteAddr = "10.0.0.10"
        val response = MockHttpServletResponse()

        ipFilter.doFilter(request, response, FilterChain { _, _ -> })

        verify { rateLimiter.check(ipRuleProperties.toRule(), "ip:203.0.113.10") }
    }
}
