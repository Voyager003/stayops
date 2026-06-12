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
    fun should_keep_member_access_policy_inside_member_application() {
        val mainRoot = Path.of("src/main/kotlin")

        val memberApplicationRoot = mainRoot.resolve("com/stayops/member/application/service")
        assertTrue(
            actual = Files.exists(memberApplicationRoot.resolve("MemberAccessApplication.kt")),
            message = "Member access policy must live under member application"
        )

        val sharedSecurityRoot = mainRoot.resolve("com/stayops/shared/security")
        assertTrue(
            actual = !Files.exists(sharedSecurityRoot.resolve("CustomerAuthChecker.kt")),
            message = "Customer access policy is not shared because it depends on Member"
        )
        assertTrue(
            actual = !Files.exists(sharedSecurityRoot.resolve("PropertyAccessChecker.kt")),
            message = "Property access policy is not shared because it depends on Member"
        )

        val memberSecurityRoot = mainRoot.resolve("com/stayops/member/infrastructure/security")
        assertTrue(
            actual = !Files.exists(memberSecurityRoot.resolve("CustomerAuthChecker.kt")),
            message = "CustomerAuthChecker must be replaced by MemberAccessApplication"
        )
        assertTrue(
            actual = !Files.exists(memberSecurityRoot.resolve("PropertyAccessChecker.kt")),
            message = "PropertyAccessChecker must be replaced by MemberAccessApplication"
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

    @Test
    fun should_use_reservation_payment_port_for_customer_reservation_payment_collaboration() {
        val mainRoot = Path.of("src/main/kotlin")

        assertTrue(
            actual = Files.exists(mainRoot.resolve("com/stayops/reservation/application/port/ReservationPaymentPort.kt")),
            message = "Customer reservation payment collaboration must be exposed as ReservationPaymentPort"
        )
        assertTrue(
            actual = Files.exists(mainRoot.resolve("com/stayops/payment/infrastructure/reservation/PaymentReservationAdapter.kt")),
            message = "Payment must adapt ReservationPaymentPort without CustomerReservationApplication depending on Payment internals"
        )

        val checkedRoots = listOf(
            mainRoot.resolve("com/stayops/reservation/application/service/CustomerReservationApplication.kt"),
            mainRoot.resolve("com/stayops/reservation/api/customer")
        )
        val forbiddenImports = listOf(
            "import com.stayops.payment.domain.model.Payment",
            "import com.stayops.payment.domain.model.PaymentCancelReason",
            "import com.stayops.payment.domain.model.PaymentOutboxMessage",
            "import com.stayops.payment.domain.model.PaymentOutboxType",
            "import com.stayops.payment.domain.model.PaymentStatus",
            "import com.stayops.payment.domain.repository.PaymentOutboxRepository",
            "import com.stayops.payment.domain.repository.PaymentRepository"
        )

        val directPaymentReferences = checkedRoots.flatMap { root ->
            if (Files.isRegularFile(root)) {
                listOf(root)
            } else {
                Files.walk(root).use { paths ->
                    paths
                        .filter { Files.isRegularFile(it) }
                        .filter { it.fileName.toString().endsWith(".kt") }
                        .toList()
                }
            }
        }.filter { path ->
            val content = Files.readString(path)
            forbiddenImports.any { content.contains(it) }
        }.map { mainRoot.relativize(it).toString() }

        assertTrue(
            actual = directPaymentReferences.isEmpty(),
            message = "Customer reservation use cases and API DTOs must depend on ReservationPaymentPort, not Payment internals: $directPaymentReferences"
        )
    }

    @Test
    fun should_keep_api_layer_from_depending_on_infrastructure_security_or_domain_repositories() {
        val mainRoot = Path.of("src/main/kotlin")
        val apiLayerViolations = Files.walk(mainRoot.resolve("com/stayops")).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .filter { path -> path.toString().contains("/api/") }
                .filter { path ->
                    val content = Files.readString(path)
                    content.contains(Regex("^import com\\.stayops\\..*\\.infrastructure\\.", RegexOption.MULTILINE)) ||
                        content.contains(Regex("^import com\\.stayops\\..*\\.domain\\.repository\\.", RegexOption.MULTILINE))
                }
                .map { mainRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = apiLayerViolations.isEmpty(),
            message = "API layer must depend on application use cases, not infrastructure security or domain repositories: $apiLayerViolations"
        )
    }

    @Test
    fun should_keep_property_api_from_creating_domain_objects_or_mutating_security_context() {
        val propertyApi = Path.of("src/main/kotlin/com/stayops/property/api/PropertyApi.kt")
        val propertyApiContent = Files.readString(propertyApi)

        val forbiddenPropertyApiReferences = listOf(
            "import com.stayops.property.domain.model.Address",
            "import com.stayops.property.domain.model.ContactInfo",
            "import org.springframework.security.authentication.UsernamePasswordAuthenticationToken",
            "import org.springframework.security.core.context.SecurityContextHolder",
            "import org.springframework.security.web.context.HttpSessionSecurityContextRepository",
            "Address.of(",
            "ContactInfo.of(",
            "SecurityContextHolder."
        ).filter { propertyApiContent.contains(it) }

        assertTrue(
            actual = forbiddenPropertyApiReferences.isEmpty(),
            message = "PropertyApi must delegate domain object creation and security context mutation: $forbiddenPropertyApiReferences"
        )

        val propertyApiDtoRoot = Path.of("src/main/kotlin/com/stayops/property/api/dto")
        val domainModelImports = Files.walk(propertyApiDtoRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) }
                .filter { it.fileName.toString().endsWith(".kt") }
                .filter { path ->
                    Files.readString(path)
                        .contains(Regex("^import com\\.stayops\\.property\\.domain\\.model\\.", RegexOption.MULTILINE))
                }
                .map { propertyApiDtoRoot.relativize(it).toString() }
                .toList()
        }

        assertTrue(
            actual = domainModelImports.isEmpty(),
            message = "Property API DTOs must not import property domain models: $domainModelImports"
        )
    }

    @Test
    fun should_place_customer_reservation_use_cases_under_reservation_not_booking() {
        val mainRoot = Path.of("src/main/kotlin")
        val testRoot = Path.of("src/test/kotlin")

        assertTrue(
            actual = !Files.exists(mainRoot.resolve("com/stayops/booking")),
            message = "Booking is not a bounded context; customer reservation use cases must live under reservation"
        )
        assertTrue(
            actual = Files.exists(mainRoot.resolve("com/stayops/reservation/application/service/CustomerReservationApplication.kt")),
            message = "Customer reservation command use case must live under the reservation context"
        )
        assertTrue(
            actual = Files.exists(mainRoot.resolve("com/stayops/reservation/application/service/ReservationSearchApplication.kt")),
            message = "Customer reservation search use case must live under the reservation context"
        )
        assertTrue(
            actual = Files.exists(mainRoot.resolve("com/stayops/member/application/service/CustomerAuthApplication.kt")),
            message = "Customer authentication is a member use case, not a booking context use case"
        )

        val bookingReferences = listOf(mainRoot, testRoot)
            .flatMap { root ->
                Files.walk(root).use { paths ->
                    paths
                        .filter { Files.isRegularFile(it) }
                        .filter { it.fileName.toString().endsWith(".kt") }
                        .filter { path ->
                            val content = Files.readString(path)
                            Regex("^\\s*(package|import) com\\.stayops\\.booking(\\.|$)", RegexOption.MULTILINE)
                                .containsMatchIn(content) || content.contains("/api/v1/" + "booking")
                        }
                        .map { root.relativize(it).toString() }
                        .toList()
                }
            }

        assertTrue(
            actual = bookingReferences.isEmpty(),
            message = "Booking package and HTTP path names must be removed from source/test code: $bookingReferences"
        )
    }
}
