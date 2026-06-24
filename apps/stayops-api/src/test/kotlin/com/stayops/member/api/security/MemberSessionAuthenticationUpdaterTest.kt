package com.stayops.member.api.security

import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class MemberSessionAuthenticationUpdaterTest {

    private val updater = MemberSessionAuthenticationUpdater()

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `member를 현재 security context의 인증 주체로 갱신한다`() {
        val member = Member.create(
            id = "member-1",
            email = "owner@test.com",
            passwordHash = "hashed",
            name = "운영자",
            role = MemberRole.OWNER
        )

        updater.update(member, MockHttpServletRequest(), MockHttpServletResponse())

        assertThat(SecurityContextHolder.getContext().authentication?.principal).isEqualTo(member)
    }
}
