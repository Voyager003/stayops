package com.stayops.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class PersistenceNamingConventionTest {

    @Test
    fun should_keep_only_repository_contracts_in_domain_repository_packages() {
        val sourceRoot = Path.of("src/main/kotlin/com/stayops")

        val violations = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.toString().contains("/domain/repository/") }
                .filter { !it.fileName.toString().endsWith("Repository.kt") }
                .map { sourceRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = violations.isEmpty(),
            message = "Domain repository packages must contain only *Repository contracts: $violations"
        )
    }

    @Test
    fun should_name_spring_data_contracts_as_dao_and_place_them_in_dao_packages() {
        val sourceRoot = Path.of("src/main/kotlin/com/stayops")

        val violations = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .filter { Files.readString(it).contains("MongoRepository<") }
                .filter { path ->
                    !path.toString().contains("/infrastructure/persistence/dao/") ||
                        !path.fileName.toString().endsWith("Dao.kt")
                }
                .map { sourceRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = violations.isEmpty(),
            message = "Spring Data contracts must be named *Dao and live in infrastructure/persistence/dao: $violations"
        )
    }
}
