package com.stayops.shared.config

import com.stayops.shared.domain.MutableClock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Instant

@TestConfiguration
class FixedTestClockConfig {

    @Bean
    @Primary
    fun fixedTestClock(): Clock = MutableClock(DEFAULT_INSTANT)

    companion object {
        val DEFAULT_INSTANT: Instant = Instant.parse("2026-05-15T10:00:00Z")
    }
}
