package com.stayops

import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.resource.DnsResolvers
import org.springframework.context.annotation.Bean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName

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

    companion object {
        @JvmStatic
        val mongoContainer: MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:8")).apply { start() }

        @JvmStatic
        val redisContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:latest"))
            .withExposedPorts(6379).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") { mongoContainer.replicaSetUrl }
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.firstMappedPort }
        }
    }
}
