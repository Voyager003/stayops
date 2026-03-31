package com.stayops.inventory.api

import com.stayops.TestcontainersConfiguration
import com.stayops.inventory.api.dto.InventoryUpdateAction
import com.stayops.inventory.api.dto.OpenInventoryRequest
import com.stayops.inventory.api.dto.UpdateInventoryRequest
import com.stayops.inventory.infrastructure.persistence.RoomInventoryMongoDataRepository
import com.stayops.room.infrastructure.persistence.RoomMongoDataRepository
import com.stayops.room.infrastructure.persistence.RoomDocument
import com.stayops.room.domain.model.RoomStatus
import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class RoomInventoryApiTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val inventoryMongoRepo: RoomInventoryMongoDataRepository,
    private val roomMongoRepo: RoomMongoDataRepository,
    private val redisTemplate: StringRedisTemplate
) {
    private lateinit var mockMvc: MockMvc

    private val pid = "prop-1"
    private val roomTypeId = "rt-1"
    private val today = LocalDate.of(2026, 3, 12)
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
        inventoryMongoRepo.deleteAll()
        roomMongoRepo.deleteAll()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()

        // 테스트용 객실 3개 생성
        val now = Instant.now()
        listOf("101", "102", "103").forEach { num ->
            roomMongoRepo.save(
                RoomDocument(
                    id = "room-$num", propertyId = pid, roomTypeId = roomTypeId,
                    roomNumber = num, floor = 1, status = RoomStatus.AVAILABLE,
                    memo = null, version = 0L, createdAt = now, updatedAt = now
                )
            )
        }
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun openRequest(startDate: LocalDate = today, endDate: LocalDate = today) =
        OpenInventoryRequest(roomTypeId = roomTypeId, startDate = startDate, endDate = endDate)

    private fun openInventory(startDate: LocalDate = today, endDate: LocalDate = today) {
        mockMvc.post("$baseUrl/inventory/open") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(openRequest(startDate, endDate))
        }
    }

    @Nested
    inner class `POST 판매 오픈` {
        @Test
        fun `유효한 요청이면 201과 생성된 일수를 반환한다`() {
            mockMvc.post("$baseUrl/inventory/open") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(openRequest(today, today.plusDays(2)))
            }.andExpect {
                status { isCreated() }
                jsonPath("$.createdDays") { value(3) }
            }
        }

        @Test
        fun `이미 오픈된 날짜는 건너뛰고 나머지만 생성한다`() {
            openInventory(today, today)

            mockMvc.post("$baseUrl/inventory/open") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(openRequest(today, today.plusDays(2)))
            }.andExpect {
                status { isCreated() }
                jsonPath("$.createdDays") { value(2) }
            }
        }
    }

    @Nested
    inner class `GET 가용성 조회` {
        @Test
        fun `날짜 범위의 재고 목록을 반환한다`() {
            openInventory(today, today.plusDays(1))

            mockMvc.get("$baseUrl/availability") {
                param("roomTypeId", roomTypeId)
                param("startDate", today.toString())
                param("endDate", today.plusDays(1).toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].totalCount") { value(3) }
            }
        }
    }

    @Nested
    inner class `PUT 재고 차단 및 해제` {
        @Test
        fun `BLOCK 요청이면 차단 후 변경된 재고를 반환한다`() {
            openInventory()

            mockMvc.put("$baseUrl/inventory/$roomTypeId/$today") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateInventoryRequest(InventoryUpdateAction.BLOCK, 2))
            }.andExpect {
                status { isOk() }
                jsonPath("$.blockedCount") { value(2) }
                jsonPath("$.availableCount") { value(1) }
            }
        }

        @Test
        fun `UNBLOCK 요청이면 차단 해제 후 변경된 재고를 반환한다`() {
            openInventory()
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
                jsonPath("$.availableCount") { value(2) }
            }
        }
    }
}
