package com.stayops.inventory.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.ROOM_INVENTORIES
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.exception.ConflictException
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringJUnitConfig(classes = [RdbRoomInventoryRepositoryTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbRoomInventoryRepositoryTest @Autowired constructor(
    private val inventoryRepository: RoomInventoryRepository,
    private val dsl: DSLContext,
    private val clock: Clock
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, FixedTestClockConfig::class, RdbRoomInventoryRepository::class)
    class TestApplication

    companion object {
        // Kept at or below the default HikariCP pool size so threads contend on the row, not on connections
        private const val THREAD_COUNT = 10
    }

    private val today = LocalDate.now(clock).plusDays(7)

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(ROOM_INVENTORIES).execute()
        dsl.deleteFrom(ROOM_TYPES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
    }

    private fun newInventory(
        id: String = "inv-1",
        propertyId: String = "prop-1",
        roomTypeId: String = "rt-1",
        date: LocalDate = today,
        totalCount: Int = 5,
        reservedCount: Int = 0,
        blockedCount: Int = 0,
        heldCount: Int = 0,
        version: Long? = null
    ) = RoomInventory.reconstitute(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        date = date,
        totalCount = totalCount,
        reservedCount = reservedCount,
        blockedCount = blockedCount,
        heldCount = heldCount,
        version = version,
        createdAt = Instant.now(clock),
        updatedAt = Instant.now(clock)
    )

    @Nested
    inner class `save 호출 시` {

        @Test
        fun `저장 후 동일한 도메인 객체를 반환한다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            val inventory = newInventory()

            val saved = inventoryRepository.save(inventory)

            assertThat(saved.id).isEqualTo(inventory.id)
            assertThat(saved.totalCount).isEqualTo(5)
            assertThat(saved.availableCount).isEqualTo(5)
            assertThat(saved.heldCount).isEqualTo(0)
            assertThat(saved.version).isEqualTo(0L)
        }

        @Test
        fun `reserve 후 저장하면 변경된 상태가 유지된다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            val inventory = inventoryRepository.save(newInventory())

            val reserved = inventory.reserve()
            val saved = inventoryRepository.save(reserved)

            val found = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)

            assertThat(saved.version).isEqualTo(1L)
            assertThat(found).isNotNull
            assertThat(found!!.reservedCount).isEqualTo(1)
            assertThat(found.availableCount).isEqualTo(4)
        }

        @Test
        fun `hold 후 저장하면 heldCount가 유지된다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            val inventory = inventoryRepository.save(newInventory())

            val held = inventoryRepository.save(inventory.hold())

            assertThat(held.heldCount).isEqualTo(1)
            assertThat(held.availableCount).isEqualTo(4)
        }
    }

    @Nested
    inner class `낙관적 락 충돌 시` {

        @Test
        fun `같은 버전으로 두 번 저장하면 ConflictException이 발생한다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            inventoryRepository.save(newInventory())

            val firstRead = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!
            val secondRead = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!

            inventoryRepository.save(firstRead.reserve())

            val exception = org.junit.jupiter.api.assertThrows<ConflictException> {
                inventoryRepository.save(secondRead.reserve())
            }

            assertThat(exception.code).isEqualTo("INVENTORY_CONFLICT")
        }
    }

    @Nested
    inner class `동시에 재고를 점유할 때` {

        @Test
        fun `같은 스냅샷을 읽은 10개 요청 중 1건만 성공하고 나머지는 충돌한다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            inventoryRepository.save(newInventory(totalCount = 1))

            // All threads share one snapshot so every save hits the version check
            val snapshot = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!

            val result = runConcurrently(THREAD_COUNT) {
                inventoryRepository.save(snapshot.hold())
            }

            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.conflictCount).isEqualTo(THREAD_COUNT - 1)
            assertThat(result.otherFailures).isEmpty()

            val stored = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!
            assertThat(stored.version).isEqualTo(1L)
            assertThat(stored.heldCount).isEqualTo(1)
            assertThat(stored.availableCount).isEqualTo(0)
        }

        @Test
        fun `각자 조회 후 점유하는 흐름에서 마지막 1객실은 1건만 점유된다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            inventoryRepository.save(newInventory(totalCount = 1))

            // Each thread reads on its own, so a loser fails either on CAS or on the domain invariant
            val result = runConcurrently(THREAD_COUNT) {
                val current = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!
                inventoryRepository.save(current.hold())
            }

            assertThat(result.successCount).isEqualTo(1)
            assertThat(result.unexpectedFailures()).isEmpty()

            val stored = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!
            assertThat(stored.heldCount).isEqualTo(1)
        }

        @Test
        fun `재고보다 많은 동시 요청이 와도 총 객실 수를 초과해 점유되지 않는다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            inventoryRepository.save(newInventory(totalCount = 3))

            val result = runConcurrently(THREAD_COUNT) {
                val current = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!
                inventoryRepository.save(current.hold())
            }

            // Without retry, losers are not re-attempted — success may fall short of 3, but never exceeds it
            assertThat(result.successCount).isBetween(1, 3)
            assertThat(result.unexpectedFailures()).isEmpty()

            val stored = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!
            assertThat(stored.heldCount).isEqualTo(result.successCount)
            assertThat(stored.heldCount + stored.reservedCount + stored.blockedCount)
                .isLessThanOrEqualTo(stored.totalCount)
        }

        @Test
        fun `서로 다른 날짜의 재고는 동시에 저장해도 충돌하지 않는다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            val snapshots = (0 until THREAD_COUNT).map { offset ->
                inventoryRepository.save(newInventory(id = "inv-$offset", date = today.plusDays(offset.toLong())))
            }

            val index = AtomicInteger(0)
            val result = runConcurrently(THREAD_COUNT) {
                val snapshot = snapshots[index.getAndIncrement()]
                inventoryRepository.save(snapshot.hold())
            }

            assertThat(result.successCount).isEqualTo(THREAD_COUNT)
            assertThat(result.conflictCount).isZero()
        }

        @Test
        fun `충돌한 요청도 최신 상태를 다시 읽어 저장하면 성공한다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            inventoryRepository.save(newInventory(totalCount = 2))

            val staleSnapshot = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!
            inventoryRepository.save(staleSnapshot.hold())

            org.junit.jupiter.api.assertThrows<ConflictException> {
                inventoryRepository.save(staleSnapshot.hold())
            }

            val refreshed = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!
            val retried = inventoryRepository.save(refreshed.hold())

            assertThat(retried.heldCount).isEqualTo(2)
            assertThat(retried.version).isEqualTo(2L)
        }
    }

    private class ConcurrentResult {
        val successCount: Int get() = successes.get()
        val conflictCount: Int get() = conflicts.get()
        val otherFailures = ConcurrentLinkedQueue<Throwable>()

        private val successes = AtomicInteger(0)
        private val conflicts = AtomicInteger(0)

        fun recordSuccess() = successes.incrementAndGet()

        fun record(failure: Throwable) {
            if (failure is ConflictException && failure.code == "INVENTORY_CONFLICT") {
                conflicts.incrementAndGet()
            } else {
                otherFailures.add(failure)
            }
        }

        // Losing a race to the domain invariant is expected once the inventory is already exhausted
        fun unexpectedFailures(): List<Throwable> =
            otherFailures.filterNot { it is IllegalArgumentException }
    }

    private fun runConcurrently(threadCount: Int, action: () -> Unit): ConcurrentResult {
        val result = ConcurrentResult()
        val executor = Executors.newFixedThreadPool(threadCount)
        val startGate = CountDownLatch(1)
        val finishGate = CountDownLatch(threadCount)

        try {
            repeat(threadCount) {
                executor.submit {
                    try {
                        startGate.await()
                        action()
                        result.recordSuccess()
                    } catch (e: Throwable) {
                        result.record(e)
                    } finally {
                        finishGate.countDown()
                    }
                }
            }
            startGate.countDown()
            check(finishGate.await(30, TimeUnit.SECONDS)) { "동시 실행이 제한 시간 내에 끝나지 않았습니다" }
        } finally {
            executor.shutdownNow()
        }
        return result
    }

    @Nested
    inner class `findByPropertyIdAndRoomTypeIdAndDate 호출 시` {

        @Test
        fun `존재하는 재고를 조회하면 반환한다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            inventoryRepository.save(newInventory())

            val found = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)

            assertThat(found).isNotNull
            assertThat(found!!.date).isEqualTo(today)
        }

        @Test
        fun `존재하지 않으면 null을 반환한다`() {
            val found = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)

            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `findByPropertyIdAndRoomTypeIdAndDateBetween 호출 시` {

        @Test
        fun `날짜 범위 내의 재고 목록을 반환한다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")
            inventoryRepository.save(newInventory(id = "inv-1", date = today))
            inventoryRepository.save(newInventory(id = "inv-2", date = today.plusDays(1)))
            inventoryRepository.save(newInventory(id = "inv-3", date = today.plusDays(2)))
            inventoryRepository.save(newInventory(id = "inv-out", date = today.plusDays(5)))

            val result = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                "prop-1", "rt-1", today, today.plusDays(2)
            )

            assertThat(result).hasSize(3)
            assertThat(result.map { it.date }).containsExactlyInAnyOrder(
                today, today.plusDays(1), today.plusDays(2)
            )
        }

        @Test
        fun `해당 범위에 재고가 없으면 빈 리스트를 반환한다`() {
            insertPropertyAndRoomType("prop-1", "rt-1")

            val result = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                "prop-1", "rt-1", today, today.plusDays(7)
            )

            assertThat(result).isEmpty()
        }
    }

    private fun insertPropertyAndRoomType(propertyId: String, roomTypeId: String) {
        insertMember("owner-1")
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        if (!dsl.fetchExists(PROPERTIES, PROPERTIES.ID.eq(propertyId))) {
            dsl.insertInto(PROPERTIES)
                .set(PROPERTIES.ID, propertyId)
                .set(PROPERTIES.OWNER_ID, "owner-1")
                .set(PROPERTIES.NAME, "Stay Ops Hotel")
                .set(PROPERTIES.TYPE, "HOTEL")
                .set(PROPERTIES.ADDRESS_STREET, "1 Test Street")
                .set(PROPERTIES.ADDRESS_CITY, "Seoul")
                .set(PROPERTIES.ADDRESS_STATE, "Seoul")
                .set(PROPERTIES.ADDRESS_ZIP_CODE, "00000")
                .set(PROPERTIES.ADDRESS_COUNTRY, "KR")
                .set(PROPERTIES.CONTACT_PHONE, "010-0000-0000")
                .set(PROPERTIES.CONTACT_EMAIL, "property@stayops.com")
                .set(PROPERTIES.DESCRIPTION, "test property")
                .set(PROPERTIES.STATUS, "ACTIVE")
                .set(PROPERTIES.TIMEZONE, "Asia/Seoul")
                .set(PROPERTIES.CURRENCY, "KRW")
                .set(PROPERTIES.VERSION, 0L)
                .set(PROPERTIES.CREATED_AT, now)
                .set(PROPERTIES.UPDATED_AT, now)
                .execute()
        }
        if (!dsl.fetchExists(ROOM_TYPES, ROOM_TYPES.ID.eq(roomTypeId))) {
            dsl.insertInto(ROOM_TYPES)
                .set(ROOM_TYPES.ID, roomTypeId)
                .set(ROOM_TYPES.PROPERTY_ID, propertyId)
                .set(ROOM_TYPES.NAME, "디럭스")
                .set(ROOM_TYPES.DESCRIPTION, "test room type")
                .set(ROOM_TYPES.MAX_OCCUPANCY, 2)
                .set(ROOM_TYPES.BASE_PRICE_AMOUNT, java.math.BigDecimal("150000.00"))
                .set(ROOM_TYPES.BASE_PRICE_CURRENCY, "KRW")
                .set(ROOM_TYPES.VERSION, 0L)
                .set(ROOM_TYPES.CREATED_AT, now)
                .set(ROOM_TYPES.UPDATED_AT, now)
                .execute()
        }
    }

    private fun insertMember(memberId: String) {
        if (dsl.fetchExists(MEMBERS, MEMBERS.ID.eq(memberId))) return

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(MEMBERS)
            .set(MEMBERS.ID, memberId)
            .set(MEMBERS.EMAIL, "$memberId@stayops.com")
            .set(MEMBERS.PASSWORD_HASH, "hashed-password")
            .set(MEMBERS.NAME, memberId)
            .set(MEMBERS.ROLE, MemberRole.OWNER.name)
            .set(MEMBERS.STATUS, MemberStatus.ACTIVE.name)
            .set(MEMBERS.VERSION, 0L)
            .set(MEMBERS.CREATED_AT, now)
            .set(MEMBERS.UPDATED_AT, now)
            .execute()
    }
}
