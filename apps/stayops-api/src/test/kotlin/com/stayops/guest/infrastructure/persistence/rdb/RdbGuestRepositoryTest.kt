package com.stayops.guest.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.model.GuestTier
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.jooq.generated.Tables.GUESTS
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringJUnitConfig(classes = [RdbGuestRepositoryTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbGuestRepositoryTest @Autowired constructor(
    private val guestRepository: GuestRepository,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, RdbGuestRepository::class)
    class TestApplication

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(GUESTS).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
    }

    private fun newGuest(
        id: String = "guest-1",
        propertyId: String = "prop-1",
        name: String = "김고객",
        phone: String = "010-0000-0000",
        email: String? = "guest@stayops.com",
        memo: String? = "메모"
    ) = Guest.create(
        id = id,
        propertyId = propertyId,
        name = name,
        phone = phone,
        email = email,
        memo = memo
    )

    @Nested
    inner class `save_및_findById` {

        @Test
        fun `저장 후 모든 필드가 보존된 Guest를 조회한다`() {
            insertProperty("prop-1")
            val guest = newGuest()
                .recordVisit(Money.of(150_000), stayNights = 2, visitDate = LocalDate.of(2026, 7, 1))

            val saved = guestRepository.save(guest)
            val found = guestRepository.findById(saved.id)

            assertThat(found).isNotNull
            assertThat(found!!.propertyId).isEqualTo("prop-1")
            assertThat(found.name).isEqualTo("김고객")
            assertThat(found.phone).isEqualTo("010-0000-0000")
            assertThat(found.email).isEqualTo("guest@stayops.com")
            assertThat(found.memo).isEqualTo("메모")
            assertThat(found.tier).isEqualTo(GuestTier.NEW)
            assertThat(found.visitSummary.totalVisits).isEqualTo(1)
            assertThat(found.visitSummary.totalSpend).isEqualTo(Money.of(150_000))
            assertThat(found.visitSummary.lastVisitDate).isEqualTo(LocalDate.of(2026, 7, 1))
            assertThat(found.visitSummary.averageStayNights).isEqualTo(2.0)
        }
    }

    @Nested
    inner class `findByPropertyIdAndPhone` {

        @Test
        fun `숙소와 전화번호로 Guest를 조회한다`() {
            insertProperty("prop-1")
            guestRepository.save(newGuest())

            val found = guestRepository.findByPropertyIdAndPhone("prop-1", "010-0000-0000")

            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo("guest-1")
        }

        @Test
        fun `다른 숙소의 같은 전화번호는 조회하지 않는다`() {
            insertProperty("prop-1")
            insertProperty("prop-2")
            guestRepository.save(newGuest(propertyId = "prop-1"))

            val found = guestRepository.findByPropertyIdAndPhone("prop-2", "010-0000-0000")

            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `findByPropertyId` {

        @Test
        fun `숙소의 전체 Guest 목록을 반환한다`() {
            insertProperty("prop-1")
            insertProperty("prop-2")
            guestRepository.save(newGuest(id = "guest-1", propertyId = "prop-1", phone = "010-0000-0001"))
            guestRepository.save(newGuest(id = "guest-2", propertyId = "prop-1", phone = "010-0000-0002"))
            guestRepository.save(newGuest(id = "guest-3", propertyId = "prop-2", phone = "010-0000-0003"))

            val result = guestRepository.findByPropertyId("prop-1")

            assertThat(result.map { it.id }).containsExactly("guest-1", "guest-2")
        }
    }

    @Nested
    inner class `findByPropertyIdAndTier` {

        @Test
        fun `숙소와 등급으로 Guest 목록을 반환한다`() {
            insertProperty("prop-1")
            val vip = (1..20).fold(newGuest(id = "guest-vip", phone = "010-0000-0001")) { guest, index ->
                guest.recordVisit(Money.of(100_000), stayNights = 1, visitDate = LocalDate.of(2026, 7, index))
            }
            guestRepository.save(vip)
            guestRepository.save(newGuest(id = "guest-new", phone = "010-0000-0002"))

            val result = guestRepository.findByPropertyIdAndTier("prop-1", GuestTier.VIP)

            assertThat(result.map { it.id }).containsExactly("guest-vip")
        }
    }

    @Nested
    inner class `findByPropertyIdAndNameContaining` {

        @Test
        fun `숙소와 이름 일부로 Guest 목록을 반환한다`() {
            insertProperty("prop-1")
            guestRepository.save(newGuest(id = "guest-1", name = "김고객", phone = "010-0000-0001"))
            guestRepository.save(newGuest(id = "guest-2", name = "이고객", phone = "010-0000-0002"))
            guestRepository.save(newGuest(id = "guest-3", name = "박예약", phone = "010-0000-0003"))

            val result = guestRepository.findByPropertyIdAndNameContaining("prop-1", "고객")

            assertThat(result.map { it.id }).containsExactly("guest-1", "guest-2")
        }
    }

    private fun insertProperty(propertyId: String) {
        insertMember("owner-$propertyId")
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dsl.insertInto(PROPERTIES)
            .set(PROPERTIES.ID, propertyId)
            .set(PROPERTIES.OWNER_ID, "owner-$propertyId")
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

    private fun insertMember(memberId: String) {
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
