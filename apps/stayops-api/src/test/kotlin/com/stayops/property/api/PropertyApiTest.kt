package com.stayops.property.api

import com.stayops.TestcontainersConfiguration
import com.stayops.property.api.dto.CreatePropertyRequest
import com.stayops.property.api.dto.UpdatePropertyRequest
import com.stayops.property.infrastructure.persistence.dao.PropertyMongoDao
import com.stayops.channel.infrastructure.persistence.dao.ChannelMongoDao
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.infrastructure.persistence.MemberDocument
import com.stayops.member.infrastructure.persistence.dao.MemberMongoDao
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PropertyApiTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val mongoDao: PropertyMongoDao,
    private val memberMongoDao: MemberMongoDao,
    private val channelMongoDao: ChannelMongoDao
) {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val admin = Member.create(
            id = "test-admin", email = "admin@test.com",
            passwordHash = "hashed", name = "테스트관리자", role = MemberRole.ADMIN
        )
        memberMongoDao.deleteAll()
        channelMongoDao.deleteAll()
        mongoDao.deleteAll()
        memberMongoDao.save(MemberDocument.from(admin))
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(admin, null, emptyList())
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun createRequest(name: String = "해운대 펜션") = CreatePropertyRequest(
        name = name,
        type = "PENSION",
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
        fun `ADMIN은 모든 숙소를 조회할 수 있다`() {
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

        @Test
        fun `OWNER는 자신의 propertyAccess에 포함된 숙소만 조회할 수 있다`() {
            // 숙소 2개 생성 (ADMIN으로)
            val createdA = mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest("펜션A"))
            }.andReturn().response.contentAsString
            val idA = objectMapper.readTree(createdA).get("id").asText()

            mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest("펜션B"))
            }

            // OWNER로 전환 — 펜션A에만 접근 권한
            val owner = Member.create(
                id = "test-owner", email = "owner@test.com",
                passwordHash = "hashed", name = "테스트운영자", role = MemberRole.OWNER
            ).grantAccess(idA, com.stayops.member.domain.model.PropertyRole.OWNER)
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(owner, null, emptyList())

            mockMvc.get("/api/v1/properties").andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].name") { value("펜션A") }
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
    inner class `PATCH 숙소 활성화` {
        @Test
        fun `INACTIVE 숙소를 활성화하면 ACTIVE로 변경된다`() {
            val created = mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest())
            }.andReturn().response.contentAsString

            val id = objectMapper.readTree(created).get("id").asText()

            mockMvc.patch("/api/v1/properties/$id/activate").andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ACTIVE") }
            }
        }

        @Test
        fun `이미 ACTIVE인 숙소를 활성화하면 400을 반환한다`() {
            val created = mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest())
            }.andReturn().response.contentAsString

            val id = objectMapper.readTree(created).get("id").asText()

            mockMvc.patch("/api/v1/properties/$id/activate")
            mockMvc.patch("/api/v1/properties/$id/activate").andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class `PATCH 숙소 비활성화` {
        @Test
        fun `ACTIVE 숙소를 비활성화하면 INACTIVE로 변경된다`() {
            val created = mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest())
            }.andReturn().response.contentAsString

            val id = objectMapper.readTree(created).get("id").asText()

            mockMvc.patch("/api/v1/properties/$id/activate")
            mockMvc.patch("/api/v1/properties/$id/deactivate").andExpect {
                status { isOk() }
                jsonPath("$.status") { value("INACTIVE") }
            }
        }

        @Test
        fun `INACTIVE 숙소를 비활성화하면 400을 반환한다`() {
            val created = mockMvc.post("/api/v1/properties") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest())
            }.andReturn().response.contentAsString

            val id = objectMapper.readTree(created).get("id").asText()

            mockMvc.patch("/api/v1/properties/$id/deactivate").andExpect {
                status { isBadRequest() }
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
