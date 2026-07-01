package com.stayops.e2e.channel

import com.stayops.TestcontainersConfiguration
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.SyncTaskRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class ChannelSyncE2ETest @Autowired constructor(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomRepository: RoomRepository,
    private val channelRepository: ChannelRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val syncTaskRepository: SyncTaskRepository,
    private val mongoTemplate: MongoTemplate,
    private val clock: Clock
) {

    private lateinit var today: LocalDate

    @BeforeEach
    fun setUp() {
        (clock as MutableClock).set(FixedTestClockConfig.DEFAULT_INSTANT)
        today = LocalDate.now(clock)

        mongoTemplate.collectionNames.forEach { name ->
            mongoTemplate.getCollection(name).deleteMany(org.bson.Document())
        }

        // Property (ACTIVE)
        propertyRepository.save(
            Property.create(
                id = "prop-sync", ownerId = "owner-1", name = "Sync 호텔",
                type = PropertyType.HOTEL,
                address = Address.of("서울시 강남구", "서울", "서울", "06000", "KR"),
                contactInfo = ContactInfo.of("02-1234-5678", "sync@test.com"),
                description = "채널 동기화 테스트 호텔", timezone = "Asia/Seoul", currency = "KRW"
            ).activate()
        )

        // RoomType
        roomTypeRepository.save(
            RoomType.create(
                id = "rt-sync", propertyId = "prop-sync", name = "스탠다드룸",
                description = "기본 객실", maxOccupancy = 2,
                basePrice = Money.won(100_000)
            )
        )

        // Room -> triggers inventory sync (90 days blocked)
        roomRepository.save(Room.create("room-sync-1", "prop-sync", "rt-sync", "101", 1))
        inventoryApplication.syncInventoryForRoomType("prop-sync", "rt-sync")

        // OTA Channel (ACTIVE)
        channelRepository.save(
            Channel.createOta(
                id = "ch-ota-sync",
                propertyId = "prop-sync",
                code = "TEST_OTA_SYNC",
                name = "테스트 OTA",
                commissionRate = BigDecimal("0.10"),
                apiEndpoint = "http://localhost:9999"
            )
        )
    }

    @Nested
    inner class `재고_오픈_시_SyncTask_생성` {

        @Test
        fun `재고_오픈_시_SyncTask가_생성된다`() {
            // When: unblock inventory for today
            inventoryApplication.unblockInventory("prop-sync", "rt-sync", today, 1)

            // Then: SyncTask exists for the OTA channel
            val tasks = syncTaskRepository.findByPropertyIdAndStatus("prop-sync", SyncTaskStatus.PENDING) +
                syncTaskRepository.findByPropertyIdAndStatus("prop-sync", SyncTaskStatus.COMPLETED)

            assertThat(tasks).isNotEmpty
            val task = tasks.first()
            assertThat(task.propertyId).isEqualTo("prop-sync")
            assertThat(task.payload["roomTypeId"]).isEqualTo("rt-sync")
            assertThat((task.payload["availableCount"] as Number).toInt()).isGreaterThan(0)
        }
    }

    @Nested
    inner class `재고_마감_시_SyncTask_생성` {

        @Test
        fun `재고_마감_시_SyncTask가_생성된다`() {
            // Given: first unblock
            inventoryApplication.unblockInventory("prop-sync", "rt-sync", today, 1)

            // Clear existing tasks to isolate block tasks
            mongoTemplate.getCollection("sync_tasks").deleteMany(org.bson.Document())

            // When: block
            inventoryApplication.blockInventory("prop-sync", "rt-sync", today, 1)

            // Then: new SyncTask created with availableCount == 0
            val tasks = syncTaskRepository.findByPropertyIdAndStatus("prop-sync", SyncTaskStatus.PENDING) +
                syncTaskRepository.findByPropertyIdAndStatus("prop-sync", SyncTaskStatus.COMPLETED)

            assertThat(tasks).isNotEmpty
            val task = tasks.first()
            assertThat((task.payload["availableCount"] as Number).toInt()).isEqualTo(0)
        }
    }
}
