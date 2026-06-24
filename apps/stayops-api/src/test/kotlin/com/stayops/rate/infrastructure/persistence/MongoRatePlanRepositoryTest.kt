package com.stayops.rate.infrastructure.persistence

import com.stayops.rate.infrastructure.persistence.dao.RatePlanMongoDao
import com.stayops.TestcontainersConfiguration
import com.stayops.rate.domain.model.DayOfWeekRate
import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.model.RatePlanType
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.DayOfWeek
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MongoRatePlanRepositoryTest @Autowired constructor(
    private val ratePlanRepository: RatePlanRepository,
    private val mongoDao: RatePlanMongoDao
) {

    @BeforeEach
    fun setUp() {
        mongoDao.deleteAll()
    }

    private fun newRatePlan(
        id: String = "rp-1",
        propertyId: String = "prop-1",
        roomTypeId: String = "rt-1",
        type: RatePlanType = RatePlanType.SEASONAL,
        dateRange: DateRange? = DateRange.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 1)),
        dayOfWeekRules: List<DayOfWeekRate>? = null,
        channelCode: String? = null,
        price: Money = Money.of(150_000),
        priority: Int = 30
    ) = RatePlan.create(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        name = "테스트 요금제",
        type = type,
        dateRange = dateRange,
        dayOfWeekRules = dayOfWeekRules,
        channelCode = channelCode,
        price = price,
        priority = priority
    )

    @Nested
    inner class Save {
        @Test
        fun `RatePlan을 저장하고 조회하면 동일한 데이터가 반환된다`() {
            val ratePlan = ratePlanRepository.save(newRatePlan())
            val found = ratePlanRepository.findById("rp-1")

            assertThat(found).isNotNull
            assertThat(found!!.name).isEqualTo("테스트 요금제")
            assertThat(found.type).isEqualTo(RatePlanType.SEASONAL)
            assertThat(found.price).isEqualTo(Money.of(150_000))
            assertThat(found.priority).isEqualTo(30)
            assertThat(found.status).isEqualTo(RatePlanStatus.ACTIVE)
        }

        @Test
        fun `DayOfWeekRate가 포함된 RatePlan을 저장하고 조회하면 유지된다`() {
            val ratePlan = newRatePlan(
                type = RatePlanType.WEEKDAY,
                dateRange = null,
                dayOfWeekRules = listOf(
                    DayOfWeekRate(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), Money.of(130_000))
                ),
                priority = 20
            )
            ratePlanRepository.save(ratePlan)

            val found = ratePlanRepository.findById("rp-1")!!
            assertThat(found.dayOfWeekRules).hasSize(1)
            assertThat(found.dayOfWeekRules!![0].daysOfWeek).containsExactlyInAnyOrder(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
            assertThat(found.dayOfWeekRules!![0].price).isEqualTo(Money.of(130_000))
        }

        @Test
        fun `dateRange가 포함된 RatePlan을 저장하고 조회하면 유지된다`() {
            ratePlanRepository.save(newRatePlan())

            val found = ratePlanRepository.findById("rp-1")!!
            assertThat(found.dateRange).isNotNull
            assertThat(found.dateRange!!.checkIn).isEqualTo(LocalDate.of(2026, 7, 1))
            assertThat(found.dateRange!!.checkOut).isEqualTo(LocalDate.of(2026, 9, 1))
        }
    }

    @Nested
    inner class FindByPropertyIdAndRoomTypeIdAndStatus {
        @Test
        fun `조건에 맞는 RatePlan 목록을 반환한다`() {
            ratePlanRepository.save(newRatePlan(id = "rp-1", priority = 30))
            ratePlanRepository.save(newRatePlan(id = "rp-2", priority = 20))

            val deactivated = newRatePlan(id = "rp-3", priority = 10).deactivate()
            ratePlanRepository.save(deactivated)

            val result = ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
                "prop-1", "rt-1", RatePlanStatus.ACTIVE
            )
            assertThat(result).hasSize(2)
        }
    }

    @Nested
    inner class FindByPropertyId {
        @Test
        fun `숙소의 전체 RatePlan 목록을 반환한다`() {
            ratePlanRepository.save(newRatePlan(id = "rp-1"))
            ratePlanRepository.save(newRatePlan(id = "rp-2", roomTypeId = "rt-2"))

            val result = ratePlanRepository.findByPropertyId("prop-1")
            assertThat(result).hasSize(2)
        }
    }

    @Nested
    inner class DeleteById {
        @Test
        fun `RatePlan을 삭제하면 조회되지 않는다`() {
            ratePlanRepository.save(newRatePlan())
            ratePlanRepository.deleteById("rp-1")

            val found = ratePlanRepository.findById("rp-1")
            assertThat(found).isNull()
        }
    }
}
