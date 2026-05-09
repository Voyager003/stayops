package com.stayops.rate.infrastructure.config

import com.stayops.rate.domain.service.RateResolverService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RateConfig {
    @Bean
    fun rateResolverService() = RateResolverService()
}
