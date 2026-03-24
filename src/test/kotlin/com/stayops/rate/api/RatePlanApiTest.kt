package com.stayops.rate.api

import com.stayops.TestcontainersConfiguration
import com.stayops.rate.api.dto.CreateRatePlanRequest
import com.stayops.rate.api.dto.DayOfWeekRuleRequest
import com.stayops.rate.domain.model.RatePlanType
import com.stayops.rate.infrastructure.persistence.RatePlanMongoDataRepository
import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class RatePlanApiTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val mongoDataRepository: RatePlanMongoDataRepository
) {
    private lateinit var mockMvc: MockMvc

    private val pid = "prop-1"
    private val baseUrl = "/api/v1/properties/$pid"

    @BeforeEach
    fun setUp() {
        val admin = Member.create(
            id = "test-admin", email = "admin@test.com",
            passwordHash = "hashed", name = "테스트관리자", role = MemberRole.ADMIN
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(admin, null, emptyList())
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        mongoDataRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun createSeasonalRequest(
        roomTypeId: String = "rt-1",
        name: String = "성수기 요금",
        price: Long = 150_000,
        priority: Int = 30
    ) = CreateRatePlanRequest(
        roomTypeId = roomTypeId,
        name = name,
        type = RatePlanType.SEASONAL,
        dateRangeStart = LocalDate.of(2026, 7, 1),
        dateRangeEnd = LocalDate.of(2026, 9, 1),
        price = price,
        priority = priority
    )

    private fun createRatePlan(request: CreateRatePlanRequest = createSeasonalRequest()): String {
        val result = mockMvc.post("$baseUrl/rate-plans") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect { status { isCreated() } }
            .andReturn().response.contentAsString
        return objectMapper.readTree(result).get("id").asText()
    }

    @Nested
    inner class `POST 요금제 생성` {
        @Test
        fun `유효한 요청이면 201과 요금제 정보를 반환한다`() {
            mockMvc.post("$baseUrl/rate-plans") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createSeasonalRequest())
            }.andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("성수기 요금") }
                jsonPath("$.type") { value("SEASONAL") }
                jsonPath("$.price") { value(150000) }
                jsonPath("$.status") { value("ACTIVE") }
            }
        }

        @Test
        fun `WEEKDAY 요금제에 요일별 요금을 설정할 수 있다`() {
            val request = CreateRatePlanRequest(
                roomTypeId = "rt-1",
                name = "주말 요금",
                type = RatePlanType.WEEKDAY,
                dayOfWeekRules = listOf(
                    DayOfWeekRuleRequest(listOf("FRIDAY", "SATURDAY"), 130_000)
                ),
                price = 100_000,
                priority = 20
            )

            mockMvc.post("$baseUrl/rate-plans") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.dayOfWeekRules.length()") { value(1) }
                jsonPath("$.dayOfWeekRules[0].price") { value(130000) }
            }
        }
    }

    @Nested
    inner class `GET 요금제 목록` {
        @Test
        fun `숙소의 요금제 목록을 반환한다`() {
            createRatePlan()
            createRatePlan(createSeasonalRequest(name = "비수기 요금", price = 80_000, priority = 30))

            mockMvc.get("$baseUrl/rate-plans")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(2) }
                }
        }
    }

    @Nested
    inner class `DELETE 요금제 삭제` {
        @Test
        fun `존재하는 요금제를 삭제하면 204를 반환한다`() {
            val id = createRatePlan()

            mockMvc.delete("$baseUrl/rate-plans/$id")
                .andExpect { status { isNoContent() } }
        }

        @Test
        fun `존재하지 않는 요금제를 삭제하면 404를 반환한다`() {
            mockMvc.delete("$baseUrl/rate-plans/unknown")
                .andExpect { status { isNotFound() } }
        }
    }

    @Nested
    inner class `GET 요금 미리보기` {
        @Test
        fun `날짜별 적용 요금을 반환한다`() {
            createRatePlan()

            mockMvc.get("$baseUrl/rates/preview") {
                param("roomTypeId", "rt-1")
                param("basePrice", "100000")
                param("startDate", "2026-07-01")
                param("endDate", "2026-07-04")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(3) }
                jsonPath("$[0].price") { value(150000) }
            }
        }

        @Test
        fun `적용 가능한 요금제가 없으면 basePrice를 반환한다`() {
            mockMvc.get("$baseUrl/rates/preview") {
                param("roomTypeId", "rt-1")
                param("basePrice", "100000")
                param("startDate", "2026-03-01")
                param("endDate", "2026-03-03")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].price") { value(100000) }
            }
        }
    }
}
