package com.stayops.room.api

import com.stayops.IntegrationTestSupport
import com.stayops.room.api.dto.CreateRoomRequest
import com.stayops.room.api.dto.UpdateRoomStatusRequest
import com.stayops.room.api.dto.RoomStatusAction
import com.stayops.room.infrastructure.persistence.RoomMongoDataRepository
import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@Import(IntegrationTestSupport.LettuceConfig::class)
class RoomApiTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val mongoDataRepository: RoomMongoDataRepository
) : IntegrationTestSupport() {
    private lateinit var mockMvc: MockMvc

    private val pid = "prop-1"
    private val baseUrl = "/api/v1/properties/$pid/rooms"

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

    private fun createRequest(roomNumber: String = "101") = CreateRoomRequest(
        roomTypeId = "rt-1",
        roomNumber = roomNumber,
        floor = 1
    )

    private fun createRoom(roomNumber: String = "101"): String {
        return mockMvc.post(baseUrl) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(createRequest(roomNumber))
        }.andReturn().response.contentAsString
    }

    @Nested
    inner class `POST 객실 생성` {
        @Test
        fun `유효한 요청이면 201과 생성된 객실을 반환한다`() {
            mockMvc.post(baseUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest())
            }.andExpect {
                status { isCreated() }
                jsonPath("$.roomNumber") { value("101") }
                jsonPath("$.status") { value("AVAILABLE") }
                jsonPath("$.propertyId") { value(pid) }
            }
        }

        @Test
        fun `roomNumber가 빈 값이면 400을 반환한다`() {
            mockMvc.post(baseUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest().copy(roomNumber = ""))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `같은 숙소에 중복 호수이면 409를 반환한다`() {
            createRoom("101")
            mockMvc.post(baseUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest("101"))
            }.andExpect {
                status { isConflict() }
            }
        }
    }

    @Nested
    inner class `GET 객실 목록 조회` {
        @Test
        fun `저장된 객실 목록을 반환한다`() {
            createRoom("101")
            createRoom("102")

            mockMvc.get(baseUrl).andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
        }
    }

    @Nested
    inner class `GET 객실 단건 조회` {
        @Test
        fun `존재하는 객실을 조회하면 200을 반환한다`() {
            val created = createRoom()
            val id = objectMapper.readTree(created).get("id").asText()

            mockMvc.get("$baseUrl/$id").andExpect {
                status { isOk() }
                jsonPath("$.id") { value(id) }
            }
        }

        @Test
        fun `존재하지 않는 id이면 404를 반환한다`() {
            mockMvc.get("$baseUrl/not-exist").andExpect {
                status { isNotFound() }
            }
        }
    }

    @Nested
    inner class `PATCH 객실 상태 전이` {
        @Test
        fun `AVAILABLE 객실에 CHECK_IN이면 OCCUPIED로 변경된다`() {
            val created = createRoom()
            val id = objectMapper.readTree(created).get("id").asText()

            mockMvc.patch("$baseUrl/$id/status") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateRoomStatusRequest(RoomStatusAction.CHECK_IN))
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("OCCUPIED") }
            }
        }

        @Test
        fun `OCCUPIED 객실에 CHECK_OUT이면 CLEANING으로 변경된다`() {
            val created = createRoom()
            val id = objectMapper.readTree(created).get("id").asText()

            mockMvc.patch("$baseUrl/$id/status") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateRoomStatusRequest(RoomStatusAction.CHECK_IN))
            }

            mockMvc.patch("$baseUrl/$id/status") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateRoomStatusRequest(RoomStatusAction.CHECK_OUT))
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CLEANING") }
            }
        }

        @Test
        fun `잘못된 상태 전이이면 400을 반환한다`() {
            val created = createRoom()
            val id = objectMapper.readTree(created).get("id").asText()

            mockMvc.patch("$baseUrl/$id/status") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateRoomStatusRequest(RoomStatusAction.CHECK_OUT))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }
}
