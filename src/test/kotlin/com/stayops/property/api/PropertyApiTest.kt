package com.stayops.property.api

import com.stayops.IntegrationTestSupport
import com.stayops.property.api.dto.CreatePropertyRequest
import com.stayops.property.api.dto.UpdatePropertyRequest
import com.stayops.property.domain.model.PropertyType
import com.stayops.property.infrastructure.persistence.PropertyMongoDataRepository
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper

class PropertyApiTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val mongoDataRepository: PropertyMongoDataRepository
) : IntegrationTestSupport() {
    private lateinit var mockMvc: MockMvc

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

    private fun createRequest(name: String = "해운대 펜션") = CreatePropertyRequest(
        ownerId = "owner-1",
        name = name,
        type = PropertyType.PENSION,
        address = CreatePropertyRequest.AddressRequest("해운대로 123", "부산", "부산광역시", "48099", "KR"),
        contactInfo = CreatePropertyRequest.ContactInfoRequest("051-123-4567", "test@pension.com"),
        description = "아름다운 펜션"
    )

    @Nested
    inner class `POST 숙소 생성` {
        @Test
        fun `유효한 요청이면 201과 생성된 숙소를 반환한다`() {
            mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest())
            }.andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("해운대 펜션") }
                jsonPath("$.status") { value("INACTIVE") }
            }
        }

        @Test
        fun `이름이 빈 값이면 400을 반환한다`() {
            val invalidRequest = createRequest().copy(name = "")

            mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(invalidRequest)
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class `GET 전체 숙소 조회` {
        @Test
        fun `저장된 숙소 목록을 반환한다`() {
            mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest("펜션A"))
            }
            mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest("펜션B"))
            }

            mockMvc.get("/api/v1/properties").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
        }
    }

    @Nested
    inner class `GET 숙소 단건 조회` {
        @Test
        fun `존재하는 숙소를 조회하면 200을 반환한다`() {
            val created = mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest())
            }.andReturn().response.contentAsString

            val id = objectMapper.readTree(created).get("id").asText()

            mockMvc.get("/api/v1/properties/$id").andExpect {
                status { isOk() }
                jsonPath("$.id") { value(id) }
            }
        }

        @Test
        fun `존재하지 않는 id이면 404를 반환한다`() {
            mockMvc.get("/api/v1/properties/not-exist").andExpect {
                status { isNotFound() }
            }
        }
    }

    @Nested
    inner class `PUT 숙소 수정` {
        @Test
        fun `존재하는 숙소를 수정하면 200과 수정된 숙소를 반환한다`() {
            val created = mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest())
            }.andReturn().response.contentAsString

            val id = objectMapper.readTree(created).get("id").asText()

            val updateRequest = UpdatePropertyRequest(
                name = "광안리 펜션",
                description = "새 설명",
                address = UpdatePropertyRequest.AddressRequest("광안리로 1", "부산", "부산광역시", "48001", "KR"),
                contactInfo = UpdatePropertyRequest.ContactInfoRequest("051-999-9999", "new@pension.com")
            )

            mockMvc.put("/api/v1/properties/$id") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(updateRequest)
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("광안리 펜션") }
            }
        }
    }
}
