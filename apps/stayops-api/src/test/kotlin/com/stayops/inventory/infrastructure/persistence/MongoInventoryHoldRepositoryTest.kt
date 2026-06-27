package com.stayops.inventory.infrastructure.persistence

import com.stayops.TestcontainersConfiguration
import com.stayops.inventory.domain.model.InventoryHold
import com.stayops.inventory.domain.model.InventoryHoldStatus
import com.stayops.inventory.domain.repository.InventoryHoldRepository
import com.stayops.inventory.infrastructure.persistence.dao.InventoryHoldMongoDao
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MongoInventoryHoldRepositoryTest @Autowired constructor(
    private val repository: InventoryHoldRepository,
    private val mongoDao: InventoryHoldMongoDao
) {

    private val now = Instant.parse("2026-04-01T01:00:00Z")
    private val dates = listOf(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2))

    @BeforeEach
    fun setUp() {
        mongoDao.deleteAll()
    }

    @Nested
    inner class `save 및 findById` {
        @Test
        fun `저장 후 모든 필드가 보존된 InventoryHold를 조회한다`() {
            repository.save(newHold())

            val found = repository.findById("hold-1")

            assertThat(found).isNotNull
            assertThat(found!!.reservationIntentId).isEqualTo("intent-1")
            assertThat(found.propertyId).isEqualTo("prop-1")
            assertThat(found.roomTypeId).isEqualTo("rt-1")
            assertThat(found.dates).containsExactlyElementsOf(dates)
            assertThat(found.quantity).isEqualTo(1)
            assertThat(found.status).isEqualTo(InventoryHoldStatus.HELD)
        }
    }

    @Nested
    inner class `active hold 조회` {
        @Test
        fun `겹치는 날짜의 만료되지 않은 hold만 반환한다`() {
            repository.save(newHold(id = "hold-active"))
            repository.save(newHold(id = "hold-expired", expiresAt = now.minusSeconds(1)))
            repository.save(newHold(id = "hold-other-date", dates = listOf(LocalDate.of(2026, 5, 10))))
            repository.save(newHold(id = "hold-released").release(now.minusSeconds(1)))

            val result = repository.findActiveByPropertyIdAndRoomTypeIdAndDates(
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                dates = listOf(LocalDate.of(2026, 5, 2)),
                now = now
            )

            assertThat(result.map { it.id }).containsExactly("hold-active")
        }

        @Test
        fun `reservationIntentId로 hold를 조회한다`() {
            repository.save(newHold())

            val found = repository.findByReservationIntentId("intent-1")

            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo("hold-1")
        }
    }

    private fun newHold(
        id: String = "hold-1",
        dates: List<LocalDate> = this.dates,
        expiresAt: Instant = now.plusSeconds(900)
    ): InventoryHold =
        InventoryHold.create(
            id = id,
            reservationIntentId = "intent-1",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            dates = dates,
            quantity = 1,
            expiresAt = expiresAt,
            now = now.minusSeconds(10)
        )
}
