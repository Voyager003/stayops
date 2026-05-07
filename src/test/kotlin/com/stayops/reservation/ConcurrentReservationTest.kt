package com.stayops.reservation

import com.stayops.TestcontainersConfiguration
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.infrastructure.persistence.ChannelDocument
import com.stayops.channel.infrastructure.persistence.ChannelMongoDataRepository
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.infrastructure.persistence.GuestDocument
import com.stayops.guest.infrastructure.persistence.GuestMongoDataRepository
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.infrastructure.persistence.RoomInventoryDocument
import com.stayops.inventory.infrastructure.persistence.RoomInventoryMongoDataRepository
import com.stayops.reservation.application.service.ReservationApplication
import com.stayops.reservation.infrastructure.persistence.ReservationMongoDataRepository
import com.stayops.room.domain.model.RoomType
import com.stayops.room.infrastructure.persistence.RoomTypeDocument
import com.stayops.room.infrastructure.persistence.RoomTypeMongoDataRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class ConcurrentReservationTest @Autowired constructor(
    private val reservationApplication: ReservationApplication,
    private val reservationMongoDataRepository: ReservationMongoDataRepository,
    private val roomTypeMongoDataRepository: RoomTypeMongoDataRepository,
    private val channelMongoDataRepository: ChannelMongoDataRepository,
    private val inventoryMongoDataRepository: RoomInventoryMongoDataRepository,
    private val guestMongoDataRepository: GuestMongoDataRepository,
    private val clock: Clock
) {

    @BeforeEach
    fun setUp() {
        reservationMongoDataRepository.deleteAll()
        inventoryMongoDataRepository.deleteAll()
        roomTypeMongoDataRepository.deleteAll()
        channelMongoDataRepository.deleteAll()
        guestMongoDataRepository.deleteAll()
    }

    @Test
    fun `마지막 1객실에 10개 동시 예약 시 정확히 1건만 성공한다`() {
        // Given: RoomType, Channel, Guest, Inventory(1객실) 준비
        val roomType = RoomType.create(
            id = "rt-1", propertyId = "prop-1",
            name = "디럭스", description = "테스트", maxOccupancy = 2,
            basePrice = Money.won(100_000)
        )
        roomTypeMongoDataRepository.save(RoomTypeDocument.from(roomType))

        val channel = Channel.createDirect(id = "ch-1", propertyId = "prop-1")
        channelMongoDataRepository.save(ChannelDocument.from(channel))

        val guest = Guest.create(id = "guest-1", propertyId = "prop-1", name = "홍길동", phone = "010-1234-5678")
        guestMongoDataRepository.save(GuestDocument.from(guest))

        val date = LocalDate.now(clock).plusDays(7)
        val now = Instant.now(clock)
        val inventory = RoomInventory.reconstitute(
            id = "inv-1", propertyId = "prop-1", roomTypeId = "rt-1",
            date = date, totalCount = 1,
            reservedCount = 0, blockedCount = 0,
            version = null,
            createdAt = now,
            updatedAt = now
        )
        inventoryMongoDataRepository.save(RoomInventoryDocument.from(inventory))

        // When: 10개 스레드가 동시에 예약 시도
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        repeat(threadCount) { i ->
            executor.submit {
                try {
                    reservationApplication.createReservation(
                        propertyId = "prop-1",
                        roomTypeId = "rt-1",
                        checkIn = date,
                        checkOut = date.plusDays(1),
                        numberOfGuests = 2,
                        guestId = "guest-1",
                        guestName = "홍길동",
                        guestPhone = "010-1234-5678",
                        guestEmail = null,
                        channelCode = "DIRECT"
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 정확히 1건만 성공
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(9)
        assertThat(reservationMongoDataRepository.count()).isEqualTo(1)
    }
}
