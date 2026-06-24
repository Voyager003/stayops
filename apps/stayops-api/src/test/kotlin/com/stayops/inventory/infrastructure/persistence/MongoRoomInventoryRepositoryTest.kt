package com.stayops.inventory.infrastructure.persistence

import com.stayops.inventory.infrastructure.persistence.dao.RoomInventoryMongoDao
import com.stayops.TestcontainersConfiguration
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.exception.ConflictException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DuplicateKeyException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class MongoRoomInventoryRepositoryTest @Autowired constructor(
    private val inventoryRepository: RoomInventoryRepository,
    private val mongoDao: RoomInventoryMongoDao,
    private val clock: Clock
) {
    private val today = LocalDate.now(clock).plusDays(7)

    @BeforeEach
    fun setUp() {
        mongoDao.deleteAll()
    }

    private fun newInventory(
        id: String = "inv-1",
        propertyId: String = "prop-1",
        roomTypeId: String = "rt-1",
        date: LocalDate = today,
        totalCount: Int = 5,
        blockedCount: Int = 0
    ) = RoomInventory.reconstitute(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        date = date,
        totalCount = totalCount,
        reservedCount = 0,
        blockedCount = blockedCount,
        version = null,
        createdAt = Instant.now(clock),
        updatedAt = Instant.now(clock)
    )

    @Nested
    inner class `save 호출 시` {
        @Test
        fun `저장 후 동일한 도메인 객체를 반환한다`() {
            val inventory = newInventory()
            val saved = inventoryRepository.save(inventory)

            assertThat(saved.id).isEqualTo(inventory.id)
            assertThat(saved.totalCount).isEqualTo(5)
            assertThat(saved.availableCount).isEqualTo(5)
        }

        @Test
        fun `reserve 후 저장하면 변경된 상태가 유지된다`() {
            val inventory = inventoryRepository.save(newInventory())

            val reserved = inventory.reserve()
            inventoryRepository.save(reserved)

            val found = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)
            assertThat(found).isNotNull
            assertThat(found!!.reservedCount).isEqualTo(1)
            assertThat(found.availableCount).isEqualTo(4)
        }

        @Test
        fun `같은 propertyId, roomTypeId, date이면 DuplicateKeyException이 발생한다`() {
            inventoryRepository.save(newInventory(id = "inv-1"))
            org.junit.jupiter.api.assertThrows<DuplicateKeyException> {
                inventoryRepository.save(newInventory(id = "inv-2"))
            }
        }

        @Test
        fun `다른 날짜이면 같은 propertyId, roomTypeId로 저장할 수 있다`() {
            inventoryRepository.save(newInventory(id = "inv-1", date = today))
            inventoryRepository.save(newInventory(id = "inv-2", date = today.plusDays(1)))

            val result = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                "prop-1", "rt-1", today, today.plusDays(1)
            )
            assertThat(result).hasSize(2)
        }
    }

    @Nested
    inner class `낙관적 락 충돌 시` {
        @Test
        fun `같은 버전으로 두 번 저장하면 ConflictException이 발생한다`() {
            // 최초 저장 (version = 0 → DB에서 version = 0으로 저장)
            val saved = inventoryRepository.save(newInventory())

            // 두 스레드가 동시에 같은 version=0 객체를 읽었다고 가정
            val firstRead = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!
            val secondRead = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDate("prop-1", "rt-1", today)!!

            // 첫 번째 요청이 먼저 저장 (version 0 → 1로 증가)
            inventoryRepository.save(firstRead.reserve())

            // 두 번째 요청이 동일한 version=0으로 저장 시도 → 충돌
            val exception = org.junit.jupiter.api.assertThrows<ConflictException> {
                inventoryRepository.save(secondRead.reserve())
            }
            assertThat(exception.code).isEqualTo("INVENTORY_CONFLICT")
        }
    }

    @Nested
    inner class `findByPropertyIdAndRoomTypeIdAndDate 호출 시` {
        @Test
        fun `존재하는 재고를 조회하면 반환한다`() {
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
            val result = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                "prop-1", "rt-1", today, today.plusDays(7)
            )
            assertThat(result).isEmpty()
        }
    }
}
