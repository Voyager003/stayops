package com.stayops.inventory

import com.stayops.TestcontainersConfiguration
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class InventoryE2ETest @Autowired constructor(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val mongoTemplate: MongoTemplate
) {

    private val today = LocalDate.now()

    @BeforeEach
    fun setUp() {
        mongoTemplate.collectionNames.forEach { name ->
            mongoTemplate.getCollection(name).deleteMany(org.bson.Document())
        }

        // Property
        propertyRepository.save(
            Property.create(
                id = "prop-inv", ownerId = "owner-1", name = "Inventory 호텔",
                type = PropertyType.HOTEL,
                address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
                contactInfo = ContactInfo.of("02-1234-5678", "inv@test.com"),
                description = "재고 테스트 호텔", timezone = "Asia/Seoul", currency = "KRW"
            ).activate()
        )

        // RoomType
        roomTypeRepository.save(
            RoomType.create(
                id = "rt-inv", propertyId = "prop-inv", name = "스탠다드룸",
                description = "기본 객실", maxOccupancy = 2,
                basePrice = Money.won(100_000)
            )
        )

        // Room (1 room) -> triggers syncInventoryForRoomType
        roomRepository.save(Room.create("room-inv-1", "prop-inv", "rt-inv", "101", 1))
        inventoryApplication.syncInventoryForRoomType("prop-inv", "rt-inv")
    }

    @Nested
    inner class `객실_추가_시_재고_기본_상태` {

        @Test
        fun `객실_추가_시_재고가_기본_닫힘_상태로_생성된다`() {
            // When: inventory already created in setUp via syncInventoryForRoomType
            val inventories = inventoryApplication.getAvailability("prop-inv", "rt-inv", today, today)

            // Then: blockedCount == 1 (totalCount), availableCount == 0
            assertThat(inventories).hasSize(1)
            assertThat(inventories[0].blockedCount).isEqualTo(1)
            assertThat(inventories[0].availableCount).isEqualTo(0)
        }
    }

    @Nested
    inner class `재고_오픈_멱등성` {

        @Test
        fun `재고_오픈_후_다시_오픈해도_에러_없이_성공한다`() {
            // First unblock
            inventoryApplication.unblockInventory("prop-inv", "rt-inv", today, 1)
            val after1 = inventoryApplication.getAvailability("prop-inv", "rt-inv", today, today)
            assertThat(after1[0].availableCount).isEqualTo(1)

            // Second unblock (idempotent — no more blocked to unblock)
            inventoryApplication.unblockInventory("prop-inv", "rt-inv", today, 1)
            val after2 = inventoryApplication.getAvailability("prop-inv", "rt-inv", today, today)
            assertThat(after2[0].availableCount).isEqualTo(1)
        }
    }

    @Nested
    inner class `재고_마감_멱등성` {

        @Test
        fun `재고_마감_후_다시_마감해도_에러_없이_성공한다`() {
            // First unblock
            inventoryApplication.unblockInventory("prop-inv", "rt-inv", today, 1)
            val afterUnblock = inventoryApplication.getAvailability("prop-inv", "rt-inv", today, today)
            assertThat(afterUnblock[0].availableCount).isEqualTo(1)

            // First block
            inventoryApplication.blockInventory("prop-inv", "rt-inv", today, 1)
            val after1 = inventoryApplication.getAvailability("prop-inv", "rt-inv", today, today)
            assertThat(after1[0].availableCount).isEqualTo(0)

            // Second block (idempotent — no more available to block)
            inventoryApplication.blockInventory("prop-inv", "rt-inv", today, 1)
            val after2 = inventoryApplication.getAvailability("prop-inv", "rt-inv", today, today)
            assertThat(after2[0].availableCount).isEqualTo(0)
        }
    }
}
