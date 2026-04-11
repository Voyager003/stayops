package com.stayops.booking.api

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.booking.application.service.CustomerAuthApplication
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.GlobalExceptionHandler
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

class BookingAuthApiTest {

    private val customerAuthApplication = mockk<CustomerAuthApplication>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(BookingAuthApi(customerAuthApplication))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    private fun sampleCustomer() = Member.create(
        id = "member-1",
        email = "customer@test.com",
        passwordHash = "hashed-password",
        name = "김고객",
        role = MemberRole.CUSTOMER
    )

    @Nested
    inner class `고객_회원가입` {

        @Test
        fun `유효한 요청이면 201을 반환한다`() {
            every { customerAuthApplication.signup("customer@test.com", "password123", "김고객") } returns sampleCustomer()

            mockMvc.post("/api/v1/booking/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"customer@test.com","password":"password123","name":"김고객"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.email") { value("customer@test.com") }
                jsonPath("$.role") { value("CUSTOMER") }
            }
        }

        @Test
        fun `중복 이메일이면 409를 반환한다`() {
            every { customerAuthApplication.signup(any(), any(), any()) } throws
                ConflictException("DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다")

            mockMvc.post("/api/v1/booking/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"existing@test.com","password":"password123","name":"김고객"}"""
            }.andExpect {
                status { isConflict() }
            }
        }

        @Test
        fun `이메일 형식이 잘못되면 400을 반환한다`() {
            mockMvc.post("/api/v1/booking/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"invalid","password":"password123","name":"김고객"}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `비밀번호가 8자 미만이면 400을 반환한다`() {
            mockMvc.post("/api/v1/booking/auth/signup") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"customer@test.com","password":"short","name":"김고객"}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class `고객_로그인` {

        @Test
        fun `CUSTOMER 계정이면 200을 반환한다`() {
            every { customerAuthApplication.login("customer@test.com", "password123") } returns sampleCustomer()

            mockMvc.post("/api/v1/booking/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"customer@test.com","password":"password123"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.email") { value("customer@test.com") }
                jsonPath("$.role") { value("CUSTOMER") }
            }
        }

        @Test
        fun `스태프 계정이면 400을 반환한다`() {
            every { customerAuthApplication.login(any(), any()) } throws
                BusinessException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.")

            mockMvc.post("/api/v1/booking/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"admin@test.com","password":"password123"}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class `고객_로그아웃` {

        @Test
        fun `로그아웃하면 204를 반환한다`() {
            mockMvc.post("/api/v1/booking/auth/logout")
                .andExpect {
                    status { isNoContent() }
                }
        }
    }
}
