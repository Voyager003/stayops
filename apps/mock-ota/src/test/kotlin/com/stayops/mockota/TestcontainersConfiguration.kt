package com.stayops.mockota

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    fun mongoDbContainer(): MongoDBContainer {
        // MongoDB 8.x refuses to start on Linux kernel 6.19+ (SERVER-121912); letting glibc own rseq works around it
        return MongoDBContainer(DockerImageName.parse("mongo:8"))
            .withEnv("GLIBC_TUNABLES", "glibc.pthread.rseq=1")
    }

    @Bean
    fun mongoDbProperties(mongo: MongoDBContainer) = DynamicPropertyRegistrar { registry ->
        registry.add("spring.mongodb.uri") { mongo.replicaSetUrl }
    }
}
