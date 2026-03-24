package com.stayops

import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.resource.DnsResolvers
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ContextConfiguration(initializers = [IntegrationTestSupport.Initializer::class])
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

    class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(ctx: ConfigurableApplicationContext) {
            TestPropertyValues.of(
                "spring.data.mongodb.uri=${mongoContainer.replicaSetUrl}",
                "spring.data.redis.host=${redisContainer.host}",
                "spring.data.redis.port=${redisContainer.firstMappedPort}",
                "toss.payments.secret-key=test_sk_test_key",
                "toss.payments.base-url=https://api.tosspayments.com/v1/payments"
            ).applyTo(ctx.environment)
        }
    }

    companion object {
        val mongoContainer: MongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:8")).apply { start() }

        val redisContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:latest"))
            .withExposedPorts(6379).apply { start() }
    }
}
