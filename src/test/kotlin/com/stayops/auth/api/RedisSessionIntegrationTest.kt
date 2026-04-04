package com.stayops.auth.api

import com.stayops.TestcontainersConfiguration
import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.auth.domain.model.PropertyRole
import com.stayops.auth.infrastructure.persistence.MemberDocument
import com.stayops.auth.infrastructure.persistence.MemberMongoDataRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class RedisSessionIntegrationTest @Autowired constructor(
    @LocalServerPort private val port: Int,
    private val memberMongoDataRepository: MemberMongoDataRepository,
    private val passwordEncoder: PasswordEncoder,
    private val redisTemplate: StringRedisTemplate
) {

    @BeforeEach
    fun setUp() {
        memberMongoDataRepository.deleteAll()
        redisTemplate.keys("spring:session:*").forEach { redisTemplate.delete(it) }

        val member = Member.create(
            id = "session-test-owner",
            email = "session@test.com",
            passwordHash = passwordEncoder.encode("password123")!!,
            name = "세션테스트",
            role = MemberRole.OWNER
        ).grantAccess("prop-1", PropertyRole.OWNER)
        memberMongoDataRepository.save(MemberDocument.from(member))
    }

    private fun post(path: String, body: String, cookie: String? = null): Pair<Int, String?> {
        val conn = URI("http://localhost:$port$path").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.instanceFollowRedirects = false
        if (cookie != null) conn.setRequestProperty("Cookie", cookie)
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toByteArray()) }
        val status = conn.responseCode
        val setCookie = conn.getHeaderField("Set-Cookie")
        conn.disconnect()
        return status to setCookie
    }

    private fun get(path: String, cookie: String? = null): Int {
        val conn = URI("http://localhost:$port$path").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        if (cookie != null) conn.setRequestProperty("Cookie", cookie)
        val status = conn.responseCode
        conn.disconnect()
        return status
    }

    private fun loginAndGetSessionCookie(): String {
        val (status, setCookie) = post(
            "/api/v1/auth/login",
            """{"email":"session@test.com","password":"password123"}"""
        )
        assertThat(status).isEqualTo(200)
        assertThat(setCookie).isNotNull()
        assertThat(setCookie).contains("SESSION=")
        return setCookie!!
    }

    private fun extractSessionId(cookie: String): String {
        val encoded = cookie.substringAfter("SESSION=").substringBefore(";")
        return String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
    }

    @Nested
    inner class `Redis 세션 저장` {

        @Test
        fun `로그인하면 Redis에 세션이 저장된다`() {
            val cookie = loginAndGetSessionCookie()
            val sessionId = extractSessionId(cookie)

            assertThat(redisTemplate.hasKey("spring:session:sessions:$sessionId"))
                .withFailMessage(
                    "Redis에 세션이 저장되지 않았습니다. cookie=%s, keys=%s",
                    cookie,
                    redisTemplate.keys("*")
                )
                .isTrue()
        }

        @Test
        fun `로그인 후 세션 쿠키로 인증된 API를 호출할 수 있다`() {
            val cookie = loginAndGetSessionCookie()

            val status = get("/api/v1/properties", cookie)
            assertThat(status).isEqualTo(200)
        }

        @Test
        fun `로그아웃하면 세션 쿠키로 인증할 수 없다`() {
            val cookie = loginAndGetSessionCookie()

            val (logoutStatus, _) = post("/api/v1/auth/logout", "", cookie)
            assertThat(logoutStatus).isEqualTo(204)

            val afterStatus = get("/api/v1/properties", cookie)
            assertThat(afterStatus).isEqualTo(401)
        }

        @Test
        fun `세션이 Redis에서 만료되면 인증할 수 없다`() {
            val cookie = loginAndGetSessionCookie()
            val sessionId = extractSessionId(cookie)

            // 세션 강제 삭제 (만료 시뮬레이션)
            redisTemplate.delete("spring:session:sessions:$sessionId")

            val afterStatus = get("/api/v1/properties", cookie)
            assertThat(afterStatus).isEqualTo(401)
        }
    }

    @Nested
    inner class `CUSTOMER 세션 만료` {

        @BeforeEach
        fun setUpCustomer() {
            val customer = Member.create(
                id = "session-test-customer",
                email = "customer@test.com",
                passwordHash = passwordEncoder.encode("password123")!!,
                name = "세션테스트고객",
                role = MemberRole.CUSTOMER
            )
            memberMongoDataRepository.save(MemberDocument.from(customer))
        }

        private fun customerLoginAndGetCookie(): String {
            val (status, setCookie) = post(
                "/api/v1/booking/auth/login",
                """{"email":"customer@test.com","password":"password123"}"""
            )
            assertThat(status).isEqualTo(200)
            assertThat(setCookie).isNotNull().contains("SESSION=")
            return setCookie!!
        }

        @Test
        fun `CUSTOMER 로그인 후 세션 쿠키로 마이페이지를 호출할 수 있다`() {
            val cookie = customerLoginAndGetCookie()

            val status = get("/api/v1/booking/my/reservations", cookie)
            assertThat(status).isEqualTo(200)
        }

        @Test
        fun `CUSTOMER 세션이 Redis에서 만료되면 401을 반환한다`() {
            val cookie = customerLoginAndGetCookie()
            val sessionId = extractSessionId(cookie)

            // 세션 강제 삭제 (만료 시뮬레이션)
            redisTemplate.delete("spring:session:sessions:$sessionId")

            val status = get("/api/v1/booking/my/reservations", cookie)
            assertThat(status).isEqualTo(401)
        }

        @Test
        fun `CUSTOMER 로그아웃 후 마이페이지를 호출하면 401을 반환한다`() {
            val cookie = customerLoginAndGetCookie()

            val (logoutStatus, _) = post("/api/v1/booking/auth/logout", "", cookie)
            assertThat(logoutStatus).isEqualTo(204)

            val status = get("/api/v1/booking/my/reservations", cookie)
            assertThat(status).isEqualTo(401)
        }
    }
}
