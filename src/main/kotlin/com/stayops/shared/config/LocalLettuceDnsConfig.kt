package com.stayops.shared.config

import io.netty.resolver.DefaultAddressResolverGroup
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("local")
class LocalLettuceDnsConfig {

    @Bean
    fun localClientResourcesBuilderCustomizer(): ClientResourcesBuilderCustomizer {
        return ClientResourcesBuilderCustomizer { builder ->
            builder.addressResolverGroup(DefaultAddressResolverGroup.INSTANCE)
        }
    }
}
