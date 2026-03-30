package com.stayops
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    fun mongoDbContainer(): GenericContainer<*> {
        return GenericContainer(DockerImageName.parse("mongo:8"))
            .withExposedPorts(27017)
            .withCommand("--replSet", "rs0")
            .waitingFor(Wait.forLogMessage(".*Waiting for connections.*", 1))
    }

    @Bean
    fun mongoDbProperties(@org.springframework.beans.factory.annotation.Qualifier("mongoDbContainer") mongo: GenericContainer<*>) = DynamicPropertyRegistrar { registry ->
        // Replica Set 초기화 (docker compose의 mongo-init과 동일)
        mongo.execInContainer(
            "mongosh", "--eval",
            "try { rs.initiate({ _id: 'rs0', members: [{ _id: 0, host: 'localhost:27017' }] }); } catch(e) { if (e.codeName !== 'AlreadyInitialized') throw e; }"
        )
        // rs.initiate() 후 Primary 선출 대기
        mongo.execInContainer(
            "mongosh", "--eval",
            "let i = 0; while (!rs.isMaster().ismaster && i++ < 30) { sleep(500); }"
        )

        val host = mongo.host
        val port = mongo.getMappedPort(27017)
        registry.add("spring.mongodb.uri") {
            "mongodb://$host:$port/test?replicaSet=rs0&directConnection=true"
        }
    }

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> {
        return GenericContainer(DockerImageName.parse("redis:latest")).withExposedPorts(6379)
    }

}
