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
                    !path.toString().contains("/infrastructure/persistence/mongo/dao/") ||
                        !path.fileName.toString().endsWith("Dao.kt")
                }
                .map { sourceRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = violations.isEmpty(),
            message = "Spring Data contracts must be named *Dao and live in infrastructure/persistence/mongo/dao: $violations"
        )
    }

    @Test
    fun should_activate_mongo_persistence_only_under_mongo_profile() {
        val sourceRoot = Path.of("src/main/kotlin/com/stayops")

        val mongoPersistenceComponentsWithoutProfile = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .filter { path ->
                    val value = path.toString()
                    value.contains("/infrastructure/persistence/") ||
                        value.endsWith("/shared/config/MongoConfig.kt") ||
                        value.endsWith("/shared/infrastructure/persistence/mongo/MongoSchedulerLock.kt")
                }
                .filter { path ->
                    val content = Files.readString(path)
                    content.contains("MongoRepository<") ||
                        content.contains("MongoTemplate") ||
                        content.contains("MongoTransactionManager")
                }
                .filter { path -> !Files.readString(path).contains("@MongoPersistence") }
                .map { sourceRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = mongoPersistenceComponentsWithoutProfile.isEmpty(),
            message = "Mongo persistence components must be active only under the mongo profile: $mongoPersistenceComponentsWithoutProfile"
        )
    }

    @Test
    fun should_define_rdb_profile_configuration_and_migrations() {
        val resourceRoot = Path.of("src/main/resources")
        val applicationConfig = Files.readString(resourceRoot.resolve("application.yml"))
        val migrationRoot = resourceRoot.resolve("db/migration")

        assertTrue(
            actual = applicationConfig.contains("active: local,mongo"),
            message = "Default application profile must keep existing Mongo behavior with local,mongo"
        )

        assertTrue(
            actual = Files.exists(resourceRoot.resolve("application-rdb.yml")),
            message = "RDB profile must define PostgreSQL, Flyway, and jOOQ settings in application-rdb.yml"
        )
        assertTrue(
            actual = Files.exists(resourceRoot.resolve("application-mongo.yml")),
            message = "Mongo profile must disable RDB auto-configuration in application-mongo.yml"
        )
        assertTrue(
            actual = Files.exists(migrationRoot),
            message = "RDB profile must manage PostgreSQL schema with Flyway migrations under db/migration"
        )

        val indexDefinitions = Files.walk(migrationRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".sql") }
                .filter { Files.readString(it).contains(Regex("""(?i)\bcreate\s+(unique\s+)?index\b""")) }
                .map { migrationRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = indexDefinitions.isEmpty(),
            message = "Initial RDB modeling migrations must define tables, PKs, and FKs only. Defer indexes to query-pattern migrations: $indexDefinitions"
        )
    }

    @Test
    fun should_keep_jooq_generated_code_inside_rdb_infrastructure() {
        val sourceRoot = Path.of("src/main/kotlin/com/stayops")
        val forbiddenJooqImports = Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .filter { path ->
                    val relativePath = sourceRoot.relativize(path).toString()
                    !relativePath.contains("/infrastructure/persistence/rdb/")
                }
                .filter { path ->
                    val content = Files.readString(path)
                    content.contains("import com.stayops.jooq.generated.") ||
                        content.contains("import org.jooq.")
                }
                .map { sourceRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = forbiddenJooqImports.isEmpty(),
            message = "jOOQ generated code and DSL types must stay inside RDB infrastructure adapters: $forbiddenJooqImports"
        )
    }
}
