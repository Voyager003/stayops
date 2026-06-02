package com.stayops.shared.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class SecurityHeadersFilterTest {

    @Test
    fun `응답에 XSS 완화 보안 헤더를 포함한다`() {
        val filter = SecurityHeadersFilter()
        val request = MockHttpServletRequest("GET", "/actuator/health")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.getHeader("Content-Security-Policy"))
            .contains("default-src 'self'")
            .contains("script-src 'self'")
            .contains("connect-src 'self' https://api.learniverse.store")
            .contains("frame-ancestors 'none'")
        assertThat(response.getHeader("Referrer-Policy"))
            .isEqualTo("strict-origin-when-cross-origin")
        assertThat(response.getHeader("Permissions-Policy"))
            .contains("camera=()")
            .contains("microphone=()")
            .contains("geolocation=()")
        assertThat(response.getHeader("X-Content-Type-Options"))
            .isEqualTo("nosniff")
    }
}
