package com.stayops.rate.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.RATE_PLAN_DAY_OF_WEEK_RULES
import com.stayops.jooq.generated.Tables.RATE_PLANS
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import com.stayops.rate.domain.model.DayOfWeekRate
import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.model.RatePlanType
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringJUnitConfig(classes = [RdbRatePlanRepositoryTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbRatePlanRepositoryTest @Autowired constructor(
    private val ratePlanRepository: RatePlanRepository,
    private val dsl: DSLContext,
    private val clock: Clock
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, FixedTestClockConfig::class, RdbRatePlanRepository::class)
    class TestApplication

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(RATE_PLAN_DAY_OF_WEEK_RULES).execute()
        dsl.deleteFrom(RATE_PLANS).execute()
        dsl.deleteFrom(ROOM_TYPES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
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
    inner class `save 호출 시` {

        @Test
        fun `RatePlan을 저장하고 조회하면 동일한 데이터가 반환된다`() {
            insertDependencies("prop-1", "rt-1")
            val ratePlan = newRatePlan()

            val saved = ratePlanRepository.save(ratePlan)

            assertThat(saved.id).isEqualTo(ratePlan.id)
            assertThat(saved.name).isEqualTo("테스트 요금제")
            assertThat(saved.type).isEqualTo(RatePlanType.SEASONAL)
            assertThat(saved.price).isEqualTo(Money.of(150_000))
            assertThat(saved.priority).isEqualTo(30)
            assertThat(saved.status).isEqualTo(RatePlanStatus.ACTIVE)
            assertThat(saved.version).isEqualTo(0L)
        }

        @Test
        fun `DayOfWeekRate가 포함된 RatePlan을 저장하고 조회하면 유지된다`() {
            insertDependencies("prop-1", "rt-1")
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
            insertDependencies("prop-1", "rt-1")
            ratePlanRepository.save(newRatePlan())

            val found = ratePlanRepository.findById("rp-1")!!
            assertThat(found.dateRange).isNotNull
            assertThat(found.dateRange!!.checkIn).isEqualTo(LocalDate.of(2026, 7, 1))
            assertThat(found.dateRange!!.checkOut).isEqualTo(LocalDate.of(2026, 9, 1))
        }

        @Test
        fun `수정 후 저장하면 편의 규칙이 교체된다`() {
            insertDependencies("prop-1", "rt-1")
            ratePlanRepository.save(
                newRatePlan(
                    dayOfWeekRules = listOf(
                        DayOfWeekRate(setOf(DayOfWeek.FRIDAY), Money.of(130_000))
                    )
                )
            )

            val updated = ratePlanRepository.findById("rp-1")!!.updateInfo(
                name = "수정된 요금제",
                dateRange = null,
                dayOfWeekRules = listOf(
                    DayOfWeekRate(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), Money.of(140_000))
                ),
                channelCode = null,
                price = Money.of(160_000),
                priority = 40
            )
            ratePlanRepository.save(updated)

            val found = ratePlanRepository.findById("rp-1")!!
            assertThat(found.name).isEqualTo("수정된 요금제")
            assertThat(found.dayOfWeekRules).hasSize(1)
            assertThat(found.dayOfWeekRules!![0].daysOfWeek).containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }
    }

    @Nested
    inner class `findByPropertyIdAndRoomTypeIdAndStatus 호출 시` {

        @Test
        fun `조건에 맞는 RatePlan 목록을 우선순위 내림차순으로 반환한다`() {
            insertDependencies("prop-1", "rt-1")
            ratePlanRepository.save(newRatePlan(id = "rp-1", priority = 10))
            ratePlanRepository.save(newRatePlan(id = "rp-2", priority = 30))
            ratePlanRepository.save(newRatePlan(id = "rp-3", priority = 20).deactivate())

            val result = ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
                "prop-1", "rt-1", RatePlanStatus.ACTIVE
            )

            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).containsExactly("rp-2", "rp-1")
        }
    }

    @Nested
    inner class `findByPropertyId 호출 시` {

        @Test
        fun `숙소의 전체 RatePlan 목록을 반환한다`() {
            insertDependencies("prop-1", "rt-1")
            insertDependencies("prop-1", "rt-2")
            ratePlanRepository.save(newRatePlan(id = "rp-1", roomTypeId = "rt-1"))
            ratePlanRepository.save(newRatePlan(id = "rp-2", roomTypeId = "rt-2"))

            val result = ratePlanRepository.findByPropertyId("prop-1")

            assertThat(result).hasSize(2)
        }
    }

    @Nested
    inner class `deleteById 호출 시` {

        @Test
        fun `RatePlan과 day_of_week 규칙을 함께 삭제한다`() {
            insertDependencies("prop-1", "rt-1")
            ratePlanRepository.save(
                newRatePlan(
                    dayOfWeekRules = listOf(
                        DayOfWeekRate(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), Money.of(130_000))
                    )
                )
            )

            ratePlanRepository.deleteById("rp-1")

            assertThat(ratePlanRepository.findById("rp-1")).isNull()
            assertThat(dsl.fetchCount(RATE_PLAN_DAY_OF_WEEK_RULES, RATE_PLAN_DAY_OF_WEEK_RULES.RATE_PLAN_ID.eq("rp-1"))).isZero()
        }
    }

    private fun insertDependencies(propertyId: String, roomTypeId: String) {
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
