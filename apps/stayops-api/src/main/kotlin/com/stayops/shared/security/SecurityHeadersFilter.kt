package com.stayops.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class SecurityHeadersFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY)
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin")
        response.setHeader("Permissions-Policy", PERMISSIONS_POLICY)
        response.setHeader("X-Content-Type-Options", "nosniff")

        filterChain.doFilter(request, response)
    }

    companion object {
        private const val CONTENT_SECURITY_POLICY =
            "default-src 'self'; " +
                "script-src 'self'; " +
                "object-src 'none'; " +
                "base-uri 'self'; " +
                "connect-src 'self' https://api.learniverse.store; " +
                "img-src 'self' data: https:; " +
                "style-src 'self' 'unsafe-inline'; " +
                "frame-ancestors 'none'; " +
                "form-action 'self'"

        private const val PERMISSIONS_POLICY =
            "camera=(), microphone=(), geolocation=(), payment=()"
    }
}
