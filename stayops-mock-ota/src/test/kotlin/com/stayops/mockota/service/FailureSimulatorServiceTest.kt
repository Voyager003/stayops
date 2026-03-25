package com.stayops.mockota.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FailureSimulatorServiceTest {

    private lateinit var service: FailureSimulatorService

    @BeforeEach
    fun setUp() {
        service = FailureSimulatorService()
    }

    @Nested
    inner class InitialState {
        @Test
        fun `should have no active mode initially`() {
            assertNull(service.getActiveMode())
        }

        @Test
        fun `should not fail initially`() {
            assertFalse(service.shouldFail())
        }
    }

    @Nested
    inner class SetFailureMode {
        @Test
        fun `should activate timeout mode`() {
            service.setFailureMode(FailureMode.timeout(5000))

            val active = service.getActiveMode()
            assertNotNull(active)
            assertEquals("TIMEOUT", active.type)
            assertEquals(5000, active.delayMs)
            assertTrue(service.shouldFail())
        }

        @Test
        fun `should activate server error mode`() {
            service.setFailureMode(FailureMode.serverError())

            val active = service.getActiveMode()
            assertNotNull(active)
            assertEquals("SERVER_ERROR", active.type)
        }

        @Test
        fun `should activate rate limit mode`() {
            service.setFailureMode(FailureMode.rateLimit())

            val active = service.getActiveMode()
            assertNotNull(active)
            assertEquals("RATE_LIMIT", active.type)
        }

        @Test
        fun `should overwrite same type mode`() {
            service.setFailureMode(FailureMode.timeout(1000))
            service.setFailureMode(FailureMode.timeout(5000))

            val active = service.getActiveMode()
            assertNotNull(active)
            assertEquals(5000, active.delayMs)
        }
    }

    @Nested
    inner class ClearAll {
        @Test
        fun `should clear all failure modes`() {
            service.setFailureMode(FailureMode.timeout())
            service.setFailureMode(FailureMode.serverError())

            service.clearAll()

            assertNull(service.getActiveMode())
            assertFalse(service.shouldFail())
        }
    }

    @Nested
    inner class FailureModeFactory {
        @Test
        fun `timeout should have default delay of 30 seconds`() {
            val mode = FailureMode.timeout()
            assertEquals(30_000, mode.delayMs)
        }

        @Test
        fun `server error should have zero delay`() {
            val mode = FailureMode.serverError()
            assertEquals(0, mode.delayMs)
        }

        @Test
        fun `rate limit should have zero delay`() {
            val mode = FailureMode.rateLimit()
            assertEquals(0, mode.delayMs)
        }
    }
}
