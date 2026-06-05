package com.stayops.mockota

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MongoConfigurationContractTest {

    @Test
    fun `application yaml should declare spring mongodb uri`() {
        val applicationYaml = readRepoFile("apps/mock-ota/src/main/resources/application.yml")

        assertTrue(applicationYaml.contains("  mongodb:\n    uri:"), "spring.mongodb.uri must be declared for Spring Boot 4")
        assertTrue(applicationYaml.contains("\${SPRING_MONGODB_URI:"), "SPRING_MONGODB_URI must remain the primary override")
        assertFalse(applicationYaml.contains("spring:\n  data:\n    mongodb:\n      uri:"), "spring.data.mongodb.uri should not be the active key")
    }

    @Test
    fun `minimal mock ota compose should inject spring mongodb uri`() {
        val compose = readRepoFile("infra/minimal/app/docker-compose.yml")

        assertTrue(compose.contains("SPRING_MONGODB_URI: mongodb://mock-ota-mongodb:27017/mock-ota"))
        assertFalse(compose.contains("SPRING_DATA_MONGODB_URI:"), "legacy env key should not be used in minimal compose")
    }

    @Test
    fun `production mock ota compose should inject spring mongodb uri`() {
        val compose = readRepoFile("infra/production/mock-ota/docker-compose.yml")

        assertTrue(compose.contains("SPRING_MONGODB_URI: mongodb://mock-ota-mongodb:27017/mock-ota"))
        assertFalse(compose.contains("SPRING_DATA_MONGODB_URI:"), "legacy env key should not be used in production mock-ota compose")
    }

    private fun readRepoFile(relativePath: String): String {
        val repoRoot = locateRepoRoot()
        return Files.readString(repoRoot.resolve(relativePath))
    }

    private fun locateRepoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (true) {
            if (Files.exists(current.resolve("infra"))) {
                return current
            }
            val parent = current.parent ?: error("Could not locate repository root from ${System.getProperty("user.dir")}")
            current = parent
        }
    }
}
