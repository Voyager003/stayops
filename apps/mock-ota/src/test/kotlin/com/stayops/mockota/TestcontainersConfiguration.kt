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
        return MongoDBContainer(DockerImageName.parse("mongo:8"))
    }

    @Bean
    fun mongoDbProperties(mongo: MongoDBContainer) = DynamicPropertyRegistrar { registry ->
        registry.add("spring.data.mongodb.uri") { mongo.replicaSetUrl }
    }
}
