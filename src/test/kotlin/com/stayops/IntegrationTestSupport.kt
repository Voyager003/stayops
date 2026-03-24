package com.stayops

import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.resource.DnsResolvers
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(IntegrationTestSupport.LettuceConfig::class)
abstract class IntegrationTestSupport {

    @TestConfiguration
    class LettuceConfig {
        @Bean(destroyMethod = "shutdown")
        fun lettuceClientResources(): ClientResources {
            return DefaultClientResources.builder()
                .dnsResolver(DnsResolvers.JVM_DEFAULT)
                .build()
        }
    }
}
