package com.stayops.rate.infrastructure.config

import com.stayops.rate.domain.service.RateResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RateConfig {
    @Bean
    fun rateResolver() = RateResolver()
}
