package com.stayops.shared.logging

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class LoadtestMdcFilterTest {

    private val filter = LoadtestMdcFilter()

    @AfterEach
    fun tearDown() {
        MDC.clear()
    }

    @Test
    fun should_put_loadtest_headers_into_mdc_during_request() {
        val request = MockHttpServletRequest("GET", "/api/v1/customer/properties")
        request.addHeader("X-Experiment-Id", "exp-20260419-001")
        request.addHeader("X-Loadtest-Phase", "db-ramp")
        request.addHeader("X-Loadtest-Scenario", "read-heavy")
        val response = MockHttpServletResponse()

        val observed = mutableMapOf<String, String?>()
        val chain = FilterChain { _, _ ->
            observed["experimentId"] = MDC.get("experimentId")
            observed["phase"] = MDC.get("phase")
            observed["scenario"] = MDC.get("scenario")
        }

        filter.doFilter(request, response, chain)

        assertEquals("exp-20260419-001", observed["experimentId"])
        assertEquals("db-ramp", observed["phase"])
        assertEquals("read-heavy", observed["scenario"])
        assertNull(MDC.get("experimentId"))
        assertNull(MDC.get("phase"))
        assertNull(MDC.get("scenario"))
    }

    @Test
    fun should_preserve_unrelated_mdc_entries_after_request() {
        MDC.put("traceId", "trace-123")
        val request = MockHttpServletRequest("GET", "/actuator/info")
        val response = MockHttpServletResponse()

        val chain = FilterChain { _, _ ->
            assertEquals("trace-123", MDC.get("traceId"))
            assertNull(MDC.get("experimentId"))
            assertNull(MDC.get("phase"))
            assertNull(MDC.get("scenario"))
        }

        filter.doFilter(request, response, chain)

        assertEquals("trace-123", MDC.get("traceId"))
    }
}
