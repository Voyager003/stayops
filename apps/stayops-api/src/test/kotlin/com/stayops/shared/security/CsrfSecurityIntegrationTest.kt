package com.stayops.shared.security

import com.stayops.TestcontainersConfiguration
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.infrastructure.persistence.MemberDocument
import com.stayops.member.infrastructure.persistence.MemberMongoDataRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CsrfSecurityIntegrationTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val memberMongoDataRepository: MemberMongoDataRepository,
    private val passwordEncoder: PasswordEncoder
) {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

        memberMongoDataRepository.deleteAll()
        memberMongoDataRepository.save(
            MemberDocument.from(
                Member.create(
                    id = "csrf-test-owner",
                    email = "csrf@test.com",
                    passwordHash = passwordEncoder.encode("password123")!!,
                    name = "CSRF 테스트",
                    role = MemberRole.OWNER
                )
            )
        )
    }

    @Test
    fun `CSRF 토큰 조회는 인증 없이 허용된다`() {
        mockMvc.get("/api/v1/csrf")
            .andExpect {
                status { isOk() }
                jsonPath("$.headerName") { value("X-CSRF-TOKEN") }
                jsonPath("$.parameterName") { value("_csrf") }
                jsonPath("$.token") { exists() }
            }
    }

    @Test
    fun `CSRF 토큰 없이 상태 변경 요청을 보내면 403을 반환한다`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"csrf@test.com","password":"password123"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `CSRF 토큰이 있으면 로그인 요청이 통과한다`() {
        mockMvc.post("/api/v1/auth/login") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"csrf@test.com","password":"password123"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value("csrf@test.com") }
        }
    }

    @Test
    fun `외부 웹훅은 CSRF 토큰 없이도 CSRF 필터에서 차단되지 않는다`() {
        val result = mockMvc.post("/api/v1/payments/toss/webhooks") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andReturn()

        assertThat(result.response.status).isNotEqualTo(403)
    }
}
