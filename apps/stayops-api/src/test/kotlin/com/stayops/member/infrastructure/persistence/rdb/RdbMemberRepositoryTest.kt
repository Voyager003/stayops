package com.stayops.member.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.jooq.generated.Tables.MEMBER_PROPERTY_ACCESSES
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.PropertyRole
import com.stayops.member.domain.repository.MemberRepository
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
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringJUnitConfig(classes = [RdbMemberRepositoryTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbMemberRepositoryTest @Autowired constructor(
    private val memberRepository: MemberRepository,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, RdbMemberRepository::class)
    class TestApplication

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(MEMBER_PROPERTY_ACCESSES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
    }

    private fun newMember(
        id: String = "member-1",
        email: String = "test@stayops.com",
        role: MemberRole = MemberRole.OWNER
    ) = Member.create(
        id = id,
        email = email,
        passwordHash = "hashed-password",
        name = "홍길동",
        role = role
    )

    @Nested
    inner class `save_및_findById` {

        @Test
        fun `저장 후 모든 필드가 보존된 Member를 조회한다`() {
            val member = newMember()
            memberRepository.save(member)
            insertProperty(propertyId = "prop-1", ownerId = member.id)

            memberRepository.save(member.grantAccess("prop-1", PropertyRole.OWNER))

            val found = memberRepository.findById("member-1")

            assertThat(found).isNotNull
            assertThat(found!!.email).isEqualTo("test@stayops.com")
            assertThat(found.name).isEqualTo("홍길동")
            assertThat(found.role).isEqualTo(MemberRole.OWNER)
            assertThat(found.propertyAccess).hasSize(1)
            assertThat(found.propertyAccess[0].propertyId).isEqualTo("prop-1")
            assertThat(found.propertyAccess[0].role).isEqualTo(PropertyRole.OWNER)
        }
    }

    @Nested
    inner class `findByEmail` {

        @Test
        fun `이메일로 Member를 조회한다`() {
            memberRepository.save(newMember())

            val found = memberRepository.findByEmail("test@stayops.com")

            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo("member-1")
        }

        @Test
        fun `존재하지 않는 이메일이면 null을 반환한다`() {
            val found = memberRepository.findByEmail("unknown@stayops.com")

            assertThat(found).isNull()
        }
    }

    @Nested
    inner class `existsByEmail` {

        @Test
        fun `존재하는 이메일이면 true를 반환한다`() {
            memberRepository.save(newMember())

            assertThat(memberRepository.existsByEmail("test@stayops.com")).isTrue()
        }

        @Test
        fun `존재하지 않는 이메일이면 false를 반환한다`() {
            assertThat(memberRepository.existsByEmail("unknown@stayops.com")).isFalse()
        }
    }

    private fun insertProperty(propertyId: String, ownerId: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(PROPERTIES)
            .set(PROPERTIES.ID, propertyId)
            .set(PROPERTIES.OWNER_ID, ownerId)
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
}
