package com.stayops

import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.resource.DnsResolvers
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName

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

    companion object {
        init {
            val mongo = MongoDBContainer(DockerImageName.parse("mongo:8")).apply { start() }
            val redis = GenericContainer(DockerImageName.parse("redis:latest"))
                .withExposedPorts(6379).apply { start() }

            System.setProperty("spring.data.mongodb.uri", mongo.replicaSetUrl)
            System.setProperty("spring.data.redis.host", redis.host)
            System.setProperty("spring.data.redis.port", redis.firstMappedPort.toString())
            System.setProperty("toss.payments.secret-key", "test_sk_test_key")
            System.setProperty("toss.payments.base-url", "https://api.tosspayments.com/v1/payments")
        }
    }
}
