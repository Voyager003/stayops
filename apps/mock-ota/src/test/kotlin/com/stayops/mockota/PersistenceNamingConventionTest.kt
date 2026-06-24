package com.stayops.mockota

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class PersistenceNamingConventionTest {

    @Test
    fun should_name_spring_data_contracts_as_dao_and_place_them_in_dao_package() {
        val sourceRoot = Path.of("src/main/kotlin/com/stayops/mockota")

        val violations = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .filter { Files.readString(it).contains("MongoRepository<") }
                .filter { path ->
                    !path.toString().contains("/dao/") ||
                        !path.fileName.toString().endsWith("Dao.kt")
                }
                .map { sourceRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = violations.isEmpty(),
            message = "Spring Data contracts must be named *Dao and live in the dao package: $violations"
        )
    }
}
