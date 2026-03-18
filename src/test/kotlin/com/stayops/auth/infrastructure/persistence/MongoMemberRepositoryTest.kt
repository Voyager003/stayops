package com.stayops.auth.infrastructure.persistence

import com.stayops.TestcontainersConfiguration
import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.auth.domain.model.PropertyRole
import com.stayops.auth.domain.repository.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class MongoMemberRepositoryTest @Autowired constructor(
    private val memberRepository: MemberRepository,
    private val mongoDataRepository: MemberMongoDataRepository
) {

    @BeforeEach
    fun setUp() {
        mongoDataRepository.deleteAll()
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
                .grantAccess("prop-1", PropertyRole.OWNER)
            memberRepository.save(member)

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
}
