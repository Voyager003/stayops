package com.stayops.reservation

import com.stayops.TestcontainersConfiguration
import com.stayops.channel.domain.model.Channel
import com.stayops.channel.infrastructure.persistence.ChannelDocument
import com.stayops.channel.infrastructure.persistence.dao.ChannelMongoDao
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.infrastructure.persistence.GuestDocument
import com.stayops.guest.infrastructure.persistence.dao.GuestMongoDao
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.infrastructure.persistence.RoomInventoryDocument
import com.stayops.inventory.infrastructure.persistence.dao.RoomInventoryMongoDao
import com.stayops.reservation.application.service.ReservationApplication
import com.stayops.reservation.infrastructure.persistence.dao.ReservationMongoDao
import com.stayops.room.domain.model.RoomType
import com.stayops.room.infrastructure.persistence.RoomTypeDocument
import com.stayops.room.infrastructure.persistence.dao.RoomTypeMongoDao
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * R-9 #1: 다중 OTA 채널 동시 예약 race condition 통합 테스트.
 *
 * 기존 ConcurrentReservationTest는 단일 채널(DIRECT) 10개 동시 호출만 검증하지만,
 * 이 테스트는 운영 환경에서 더 현실적인 시나리오인 "서로 다른 채널이 동시에 마지막
 * 1실을 경쟁"하는 race condition을 검증한다.
 *
 * 검증 목표:
 * 1) 3개 서로 다른 채널(DIRECT, AGODA, BOOKING)이 같은 객실을 동시에 예약 시도해도
 *    정확히 1건만 성공하고 나머지는 ConflictException으로 거부된다
 * 2) 성공한 예약의 channelCode는 비결정적이지만(스레드 스케줄러 의존),
 *    DB에 저장된 예약은 정확히 1건이며 inventory.reservedCount = 1이다
 * 3) 실패한 채널들은 부수 효과를 남기지 않는다 (트랜잭션 롤백)
 *
 * 핵심 메커니즘:
 * - RoomInventory의 @Version (낙관적 락)이 동시 수정을 차단
 * - MongoDB Replica Set이 트랜잭션 격리 보장
 * - R-2-a에서 도입한 ConflictException 변환이 충돌을 도메인 예외로 표현
 */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class MultiChannelConcurrentReservationTest @Autowired constructor(
    private val reservationApplication: ReservationApplication,
    private val reservationMongoDao: ReservationMongoDao,
    private val roomTypeMongoDao: RoomTypeMongoDao,
    private val channelMongoDao: ChannelMongoDao,
    private val inventoryMongoDao: RoomInventoryMongoDao,
    private val guestMongoDao: GuestMongoDao,
    private val clock: Clock
) {

    private val propertyId = "prop-multi"
    private val roomTypeId = "rt-multi"
    private lateinit var date: LocalDate

    @BeforeEach
    fun setUp() {
        date = LocalDate.now(clock).plusDays(7)

        reservationMongoDao.deleteAll()
        inventoryMongoDao.deleteAll()
        roomTypeMongoDao.deleteAll()
        channelMongoDao.deleteAll()
        guestMongoDao.deleteAll()

        // RoomType
        val roomType = RoomType.create(
            id = roomTypeId, propertyId = propertyId,
            name = "스탠다드", description = "마지막 1실 테스트", maxOccupancy = 2,
            basePrice = Money.won(150_000)
        )
        roomTypeMongoDao.save(RoomTypeDocument.from(roomType))

        // 3개 채널: DIRECT + 2개 OTA
        val direct = Channel.createDirect(id = "ch-direct", propertyId = propertyId)
        val agoda = Channel.createOta(
            id = "ch-agoda", propertyId = propertyId,
            code = "AGODA", name = "Agoda",
            commissionRate = BigDecimal("0.15"),
            apiEndpoint = "https://mock-ota/agoda"
        )
        val booking = Channel.createOta(
            id = "ch-booking", propertyId = propertyId,
            code = "BOOKING", name = "Booking.com",
            commissionRate = BigDecimal("0.18"),
            apiEndpoint = "https://mock-ota/booking"
        )
        channelMongoDao.save(ChannelDocument.from(direct))
        channelMongoDao.save(ChannelDocument.from(agoda))
        channelMongoDao.save(ChannelDocument.from(booking))

        // Guest 3명 (각 채널별 다른 고객)
        listOf("guest-direct", "guest-agoda", "guest-booking").forEachIndexed { i, gid ->
            val guest = Guest.create(
                id = gid, propertyId = propertyId,
                name = "Customer-$i", phone = "010-0000-${1000 + i}"
            )
            guestMongoDao.save(GuestDocument.from(guest))
        }

        // Inventory: 마지막 1실 (totalCount=1, reservedCount=0, blockedCount=0)
        val now = Instant.now(clock)
        val inventory = RoomInventory.reconstitute(
            id = "inv-multi", propertyId = propertyId, roomTypeId = roomTypeId,
            date = date, totalCount = 1, reservedCount = 0, blockedCount = 0,
            version = null,
            createdAt = now,
            updatedAt = now
        )
        inventoryMongoDao.save(RoomInventoryDocument.from(inventory))
    }

    @Test
    fun `DIRECT_AGODA_BOOKING이 동시에 마지막 1실을 예약하면 정확히 1건만 성공한다`() {
        // Given: setUp 완료 — 3개 채널, 1실 가용
        val channels = listOf(
            Triple("DIRECT", "guest-direct", "Customer-0"),
            Triple("AGODA", "guest-agoda", "Customer-1"),
            Triple("BOOKING", "guest-booking", "Customer-2")
        )

        val latch = CountDownLatch(channels.size)
        val executor = Executors.newFixedThreadPool(channels.size)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val successChannels = ConcurrentLinkedQueue<String>()
        val failureMessages = ConcurrentLinkedQueue<String>()

        // When: 3개 채널이 동시에 같은 객실 예약 시도
        channels.forEach { (channelCode, guestId, guestName) ->
            executor.submit {
                try {
                    reservationApplication.createReservation(
                        propertyId = propertyId,
                        roomTypeId = roomTypeId,
                        checkIn = date,
                        checkOut = date.plusDays(1),
                        numberOfGuests = 2,
                        guestId = guestId,
                        guestName = guestName,
                        guestPhone = "010-0000-0000",
                        guestEmail = null,
                        channelCode = channelCode
                    )
                    successCount.incrementAndGet()
                    successChannels.add(channelCode)
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                    failureMessages.add("$channelCode: ${e.javaClass.simpleName}: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Then: 정확히 1건만 성공
        assertThat(successCount.get())
            .withFailMessage(
                "동시 예약 race condition 검증 실패. 성공: %d, 실패: %d, 성공 채널: %s, 실패 사유: %s",
                successCount.get(), failCount.get(), successChannels, failureMessages
            )
            .isEqualTo(1)
        assertThat(failCount.get()).isEqualTo(2)

        // Then: DB에 정확히 1건의 Reservation만 존재
        assertThat(reservationMongoDao.count()).isEqualTo(1)

        // Then: Inventory의 reservedCount는 정확히 1
        val finalInventory = inventoryMongoDao.findAll().first()
        assertThat(finalInventory.reservedCount)
            .withFailMessage(
                "Inventory.reservedCount=%d (예상: 1). over-reservation 또는 부분 차감 잔존 가능성",
                finalInventory.reservedCount
            )
            .isEqualTo(1)

        // Then: 성공한 채널은 DIRECT, AGODA, BOOKING 중 하나 (비결정적)
        assertThat(successChannels).hasSize(1)
        assertThat(successChannels.first()).isIn("DIRECT", "AGODA", "BOOKING")

        // Then: 실패한 채널 2개의 예외 메시지에 ConflictException 또는 IllegalArgumentException이 포함됨
        // (ConflictException = 낙관적 락 충돌, IllegalArgumentException = availableCount < 1)
        assertThat(failureMessages).hasSize(2)
        failureMessages.forEach { msg ->
            assertThat(msg).matches(".*(?i)(Conflict|IllegalArgument|가용 객실).*")
        }
    }
}
