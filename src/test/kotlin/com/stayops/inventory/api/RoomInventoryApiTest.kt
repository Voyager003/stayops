package com.stayops.inventory.api

import com.stayops.TestcontainersConfiguration
import com.stayops.inventory.api.dto.InitializeInventoryRequest
import com.stayops.inventory.api.dto.InventoryUpdateAction
import com.stayops.inventory.api.dto.UpdateInventoryRequest
import com.stayops.inventory.infrastructure.persistence.RoomInventoryMongoDataRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class RoomInventoryApiTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val mongoDataRepository: RoomInventoryMongoDataRepository,
    private val redisTemplate: StringRedisTemplate
) {
    private lateinit var mockMvc: MockMvc

    private val pid = "prop-1"
    private val roomTypeId = "rt-1"
    private val today = LocalDate.of(2026, 3, 12)
    private val baseUrl = "/api/v1/properties/$pid"

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        mongoDataRepository.deleteAll()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    private fun initializeRequest(date: LocalDate = today, totalCount: Int = 5) =
        InitializeInventoryRequest(roomTypeId = roomTypeId, date = date, totalCount = totalCount)

    private fun initializeInventory(date: LocalDate = today, totalCount: Int = 5): String {
        return mockMvc.post("$baseUrl/inventory/initialize") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(initializeRequest(date, totalCount))
        }.andReturn().response.contentAsString
    }

    @Nested
    inner class `POST 재고 초기화` {
        @Test
        fun `유효한 요청이면 201과 재고 정보를 반환한다`() {
            mockMvc.post("$baseUrl/inventory/initialize") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(initializeRequest())
            }.andExpect {
                status { isCreated() }
                jsonPath("$.date") { value("2026-03-12") }
                jsonPath("$.totalCount") { value(5) }
                jsonPath("$.availableCount") { value(5) }
                jsonPath("$.reservedCount") { value(0) }
                jsonPath("$.blockedCount") { value(0) }
            }
        }

        @Test
        fun `같은 날짜에 재고가 이미 있으면 409를 반환한다`() {
            initializeInventory()
            mockMvc.post("$baseUrl/inventory/initialize") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(initializeRequest())
            }.andExpect {
                status { isConflict() }
            }
        }

        @Test
        fun `totalCount가 0이면 400을 반환한다`() {
            mockMvc.post("$baseUrl/inventory/initialize") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(initializeRequest(totalCount = 0))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class `GET 가용성 조회` {
        @Test
        fun `날짜 범위의 재고 목록을 반환한다`() {
            initializeInventory(date = today, totalCount = 5)
            initializeInventory(date = today.plusDays(1), totalCount = 3)

            mockMvc.get("$baseUrl/availability") {
                param("roomTypeId", roomTypeId)
                param("startDate", today.toString())
                param("endDate", today.plusDays(1).toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
            }
        }

        @Test
        fun `해당 범위에 재고가 없으면 빈 배열을 반환한다`() {
            mockMvc.get("$baseUrl/availability") {
                param("roomTypeId", roomTypeId)
                param("startDate", today.toString())
                param("endDate", today.plusDays(7).toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
            }
        }
    }

    @Nested
    inner class `PUT 재고 차단 및 해제` {
        @Test
        fun `BLOCK 요청이면 차단 후 변경된 재고를 반환한다`() {
            initializeInventory(totalCount = 5)

            mockMvc.put("$baseUrl/inventory/$roomTypeId/$today") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateInventoryRequest(InventoryUpdateAction.BLOCK, 2))
            }.andExpect {
                status { isOk() }
                jsonPath("$.blockedCount") { value(2) }
                jsonPath("$.availableCount") { value(3) }
            }
        }

        @Test
        fun `UNBLOCK 요청이면 차단 해제 후 변경된 재고를 반환한다`() {
            initializeInventory(totalCount = 5)
            mockMvc.put("$baseUrl/inventory/$roomTypeId/$today") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateInventoryRequest(InventoryUpdateAction.BLOCK, 3))
            }

            mockMvc.put("$baseUrl/inventory/$roomTypeId/$today") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateInventoryRequest(InventoryUpdateAction.UNBLOCK, 2))
            }.andExpect {
                status { isOk() }
                jsonPath("$.blockedCount") { value(1) }
                jsonPath("$.availableCount") { value(4) }
            }
        }

        @Test
        fun `존재하지 않는 재고에 차단하면 404를 반환한다`() {
            mockMvc.put("$baseUrl/inventory/$roomTypeId/$today") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateInventoryRequest(InventoryUpdateAction.BLOCK, 1))
            }.andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `가용 재고보다 많이 차단하면 400을 반환한다`() {
            initializeInventory(totalCount = 3)

            mockMvc.put("$baseUrl/inventory/$roomTypeId/$today") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateInventoryRequest(InventoryUpdateAction.BLOCK, 5))
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }
}
