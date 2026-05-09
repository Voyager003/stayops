package com.stayops.shared.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class LoadtestMdcFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        putIfPresent("experimentId", request.getHeader("X-Experiment-Id"))
        putIfPresent("phase", request.getHeader("X-Loadtest-Phase"))
        putIfPresent("scenario", request.getHeader("X-Loadtest-Scenario"))

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("experimentId")
            MDC.remove("phase")
            MDC.remove("scenario")
        }
    }

    private fun putIfPresent(key: String, value: String?) {
        val sanitized = value
            ?.trim()
            ?.replace(CONTROL_CHARACTERS, "_")
            ?.take(MAX_MDC_VALUE_LENGTH)

        if (!sanitized.isNullOrBlank()) {
            MDC.put(key, sanitized)
        }
    }

    companion object {
        private const val MAX_MDC_VALUE_LENGTH = 128
        private val CONTROL_CHARACTERS = Regex("[\\r\\n\\t]")
    }
}
