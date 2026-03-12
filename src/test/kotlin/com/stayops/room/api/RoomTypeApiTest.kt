package com.stayops.room.api

import com.stayops.TestcontainersConfiguration
import com.stayops.room.api.dto.CreateRoomTypeRequest
import com.stayops.room.api.dto.UpdateRoomTypeRequest
import com.stayops.room.infrastructure.persistence.RoomTypeMongoDataRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class RoomTypeApiTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val mongoDataRepository: RoomTypeMongoDataRepository
) {
    private lateinit var mockMvc: MockMvc

    private val pid = "prop-1"
    private val baseUrl = "/api/v1/properties/$pid/room-types"

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        mongoDataRepository.deleteAll()
    }

    private fun createRequest(name: String = "디럭스 더블") = CreateRoomTypeRequest(
        name = name,
        description = "넓은 더블룸",
        maxOccupancy = 2,
        basePrice = BigDecimal("150000"),
        amenities = listOf("TV", "에어컨")
    )

    private fun createRoomType(name: String = "디럭스 더블"): String {
        return mockMvc.post(baseUrl) {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(createRequest(name))
        }.andReturn().response.contentAsString
    }

    @Nested
    inner class `POST 객실타입 생성` {
        @Test
        fun `유효한 요청이면 201과 생성된 객실타입을 반환한다`() {
            mockMvc.post(baseUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest())
            }.andExpect {
                status { isCreated() }
                jsonPath("$.name") { value("디럭스 더블") }
                jsonPath("$.status") { value("INACTIVE") }
                jsonPath("$.propertyId") { value(pid) }
                jsonPath("$.basePrice.amount") { value(150000) }
            }
        }

        @Test
        fun `이름이 빈 값이면 400을 반환한다`() {
            mockMvc.post(baseUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest().copy(name = ""))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `같은 숙소에 중복 이름이면 409를 반환한다`() {
            createRoomType("디럭스")
            mockMvc.post(baseUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(createRequest("디럭스"))
            }.andExpect {
                status { isConflict() }
            }
        }
    }

    @Nested
    inner class `GET 객실타입 목록 조회` {
        @Test
        fun `저장된 목록을 반환한다`() {
            createRoomType("디럭스")
            createRoomType("스위트")

            mockMvc.get(baseUrl).andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
        }
    }

    @Nested
    inner class `GET 객실타입 단건 조회` {
        @Test
        fun `존재하는 객실타입을 조회하면 200을 반환한다`() {
            val created = createRoomType()
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
    inner class `PUT 객실타입 수정` {
        @Test
        fun `존재하는 객실타입을 수정하면 200과 수정된 결과를 반환한다`() {
            val created = createRoomType()
            val id = objectMapper.readTree(created).get("id").asText()

            val updateRequest = UpdateRoomTypeRequest(
                name = "스위트룸",
                description = "최고급 스위트",
                maxOccupancy = 4,
                basePrice = BigDecimal("300000")
            )

            mockMvc.put("$baseUrl/$id") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(updateRequest)
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("스위트룸") }
                jsonPath("$.maxOccupancy") { value(4) }
            }
        }
    }

    @Nested
    inner class `DELETE 객실타입 비활성화` {
        @Test
        fun `ACTIVE 상태의 객실타입을 비활성화하면 204를 반환한다`() {
            val created = createRoomType()
            val id = objectMapper.readTree(created).get("id").asText()

            // activate first
            val roomType = mongoDataRepository.findById(id).get()
            mongoDataRepository.save(roomType.copy(status = com.stayops.room.domain.model.RoomTypeStatus.ACTIVE))

            mockMvc.delete("$baseUrl/$id").andExpect {
                status { isNoContent() }
            }
        }

        @Test
        fun `존재하지 않는 id이면 404를 반환한다`() {
            mockMvc.delete("$baseUrl/not-exist").andExpect {
                status { isNotFound() }
            }
        }
    }
}
