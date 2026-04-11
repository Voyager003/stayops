package com.stayops.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ServiceNamingConventionTest {

    @Test
    fun should_name_application_facades_as_application_and_domain_services_as_service() {
        val sourceRoot = Path.of("src/main/kotlin/com/stayops")

        val applicationServiceFiles = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.toString().contains("/application/service/") }
                .filter { it.fileName.toString().endsWith("Service.kt") }
                .map { sourceRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = applicationServiceFiles.isEmpty(),
            message = "Application facade files must use *Application naming, not *Service: $applicationServiceFiles"
        )

        val rateResolverService = sourceRoot.resolve("rate/domain/service/RateResolverService.kt")
        assertTrue(
            actual = Files.exists(rateResolverService),
            message = "RateResolver is a domain service and must be named RateResolverService"
        )
    }
}
