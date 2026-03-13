package com.stayops

import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DefaultClientResources
import io.lettuce.core.resource.DnsResolvers
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun mongoDbContainer(): MongoDBContainer {
        return MongoDBContainer(DockerImageName.parse("mongo:latest"))
    }

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> {
        return GenericContainer(DockerImageName.parse("redis:latest")).withExposedPorts(6379)
    }

    // Netty's async DNS resolver cannot resolve 'localhost' from /etc/hosts.
    // Use JVM's built-in resolver instead so Testcontainers Redis connections work.
    @Bean(destroyMethod = "shutdown")
    fun lettuceClientResources(): ClientResources {
        return DefaultClientResources.builder()
            .dnsResolver(DnsResolvers.JVM_DEFAULT)
            .build()
    }

}
