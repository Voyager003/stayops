package com.stayops.auth.api

import com.stayops.auth.application.service.AuthService
import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.GlobalExceptionHandler
import com.stayops.shared.exception.NotFoundException
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpSession
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class AuthApiTest {

    private val authService = mockk<AuthService>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(AuthApi(authService))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    private fun sampleMember() = Member.create(
        id = "member-1",
        email = "test@stayops.com",
        passwordHash = "hashed-password",
        name = "홍길동",
        role = MemberRole.OWNER
    )

    @Nested
    inner class `회원가입` {

        @Test
        fun `유효한 요청이면 201을 반환한다`() {
            every { authService.signup("new@stayops.com", "password123", "홍길동") } returns sampleMember()

            mockMvc.post("/api/v1/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"new@stayops.com","password":"password123","name":"홍길동"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.email") { value("test@stayops.com") }
                jsonPath("$.role") { value("OWNER") }
            }
        }

        @Test
        fun `중복 이메일이면 409를 반환한다`() {
            every { authService.signup(any(), any(), any()) } throws
                ConflictException("DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다")

            mockMvc.post("/api/v1/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"existing@stayops.com","password":"password123","name":"홍길동"}"""
            }.andExpect {
                status { isConflict() }
            }
        }

        @Test
        fun `이메일 형식이 잘못되면 400을 반환한다`() {
            mockMvc.post("/api/v1/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"invalid","password":"password123","name":"홍길동"}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class `로그인` {

        @Test
        fun `유효한 자격증명이면 200을 반환한다`() {
            every { authService.login("test@stayops.com", "password123") } returns sampleMember()

            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"test@stayops.com","password":"password123"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.email") { value("test@stayops.com") }
                jsonPath("$.name") { value("홍길동") }
            }
        }

        @Test
        fun `존재하지 않는 이메일이면 404를 반환한다`() {
            every { authService.login(any(), any()) } throws
                NotFoundException("MEMBER_NOT_FOUND", "존재하지 않는 이메일입니다")

            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"unknown@stayops.com","password":"password123"}"""
            }.andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `비밀번호가 틀리면 400을 반환한다`() {
            every { authService.login(any(), any()) } throws
                BusinessException("INVALID_PASSWORD", "비밀번호가 일치하지 않습니다")

            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"test@stayops.com","password":"wrong"}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class `로그아웃` {

        @Test
        fun `로그아웃하면 204를 반환한다`() {
            every { authService.logout(any<HttpSession>()) } returns Unit

            mockMvc.post("/api/v1/auth/logout")
                .andExpect {
                    status { isNoContent() }
                }

            verify { authService.logout(any<HttpSession>()) }
        }
    }
}
