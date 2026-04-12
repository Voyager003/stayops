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

    @Test
    fun should_name_member_bounded_context_as_member_not_auth() {
        val mainRoot = Path.of("src/main/kotlin")
        val testRoot = Path.of("src/test/kotlin")

        val authPackageReferences = listOf(mainRoot, testRoot)
            .flatMap { root ->
                Files.walk(root).use { paths ->
                    paths
                        .filter { Files.isRegularFile(it) }
                        .filter { it.fileName.toString().endsWith(".kt") }
                        .filter { path ->
                            val content = Files.readString(path)
                            Regex("^\\s*(package|import) com\\.stayops\\.auth(\\.|$)", RegexOption.MULTILINE)
                                .containsMatchIn(content)
                        }
                        .map { root.relativize(it).toString() }
                        .toList()
                }
            }

        assertTrue(
            actual = authPackageReferences.isEmpty(),
            message = "Member bounded context must use com.stayops.member packages, not com.stayops.auth: $authPackageReferences"
        )

        assertTrue(
            actual = Files.exists(mainRoot.resolve("com/stayops/member/domain/model/Member.kt")),
            message = "Member aggregate must live under the member bounded context package"
        )
    }

    @Test
    fun should_keep_member_security_checkers_inside_member_infrastructure() {
        val mainRoot = Path.of("src/main/kotlin")

        val memberSecurityRoot = mainRoot.resolve("com/stayops/member/infrastructure/security")
        assertTrue(
            actual = Files.exists(memberSecurityRoot.resolve("CustomerAuthChecker.kt")),
            message = "CustomerAuthChecker depends on Member and must live under member infrastructure"
        )
        assertTrue(
            actual = Files.exists(memberSecurityRoot.resolve("PropertyAccessChecker.kt")),
            message = "PropertyAccessChecker depends on Member and must live under member infrastructure"
        )

        val sharedSecurityRoot = mainRoot.resolve("com/stayops/shared/security")
        assertTrue(
            actual = !Files.exists(sharedSecurityRoot.resolve("CustomerAuthChecker.kt")),
            message = "CustomerAuthChecker is not shared because it depends on Member"
        )
        assertTrue(
            actual = !Files.exists(sharedSecurityRoot.resolve("PropertyAccessChecker.kt")),
            message = "PropertyAccessChecker is not shared because it depends on Member"
        )
    }

    @Test
    fun should_use_inventory_reservation_port_for_reserve_release_consumers() {
        val mainRoot = Path.of("src/main/kotlin")

        assertTrue(
            actual = Files.exists(mainRoot.resolve("com/stayops/inventory/application/port/InventoryReservationPort.kt")),
            message = "Inventory reserve/release contract must be exposed as InventoryReservationPort"
        )

        val directInventoryApplicationReferences = listOf(
            "com/stayops/booking",
            "com/stayops/reservation",
            "com/stayops/channel",
            "com/stayops/payment"
        ).flatMap { relativeDir ->
            val root = mainRoot.resolve(relativeDir)
            Files.walk(root).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .filter { it.fileName.toString().endsWith(".kt") }
                    .filter { path ->
                        Files.readString(path)
                            .contains("import com.stayops.inventory.application.service.RoomInventoryApplication")
                    }
                    .map { mainRoot.relativize(it).toString() }
                    .toList()
            }
        }

        assertTrue(
            actual = directInventoryApplicationReferences.isEmpty(),
            message = "External reserve/release consumers must depend on InventoryReservationPort, not RoomInventoryApplication: $directInventoryApplicationReferences"
        )
    }

    @Test
    fun should_use_availability_sync_port_for_inventory_to_channel_sync() {
        val mainRoot = Path.of("src/main/kotlin")

        assertTrue(
            actual = Files.exists(mainRoot.resolve("com/stayops/inventory/application/port/AvailabilitySyncPort.kt")),
            message = "Inventory availability sync must be exposed as AvailabilitySyncPort"
        )

        assertTrue(
            actual = Files.exists(mainRoot.resolve("com/stayops/channel/infrastructure/sync/ChannelAvailabilitySyncAdapter.kt")),
            message = "Channel must adapt Inventory AvailabilitySyncPort without Inventory depending on ChannelSyncApplication"
        )

        val directChannelApplicationReferences = Files.walk(mainRoot.resolve("com/stayops/inventory")).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .filter { path ->
                    Files.readString(path)
                        .contains("import com.stayops.channel.application.service.ChannelSyncApplication")
                }
                .map { mainRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = directChannelApplicationReferences.isEmpty(),
            message = "Inventory must depend on AvailabilitySyncPort, not ChannelSyncApplication: $directChannelApplicationReferences"
        )
    }

    @Test
    fun should_use_room_inventory_sync_port_for_room_to_inventory_sync() {
        val mainRoot = Path.of("src/main/kotlin")

        assertTrue(
            actual = Files.exists(mainRoot.resolve("com/stayops/inventory/application/port/RoomInventorySyncPort.kt")),
            message = "Room-triggered inventory sync must be exposed as RoomInventorySyncPort"
        )

        val directInventoryApplicationReferences = Files.walk(mainRoot.resolve("com/stayops/room")).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .filter { path ->
                    Files.readString(path)
                        .contains("import com.stayops.inventory.application.service.RoomInventoryApplication")
                }
                .map { mainRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = directInventoryApplicationReferences.isEmpty(),
            message = "Room must depend on RoomInventorySyncPort, not RoomInventoryApplication: $directInventoryApplicationReferences"
        )
    }
}
