package com.stayops.inventory.api

import com.stayops.TestcontainersConfiguration
import com.stayops.inventory.api.dto.InventoryUpdateAction
import com.stayops.inventory.api.dto.UpdateInventoryRequest
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.inventory.infrastructure.persistence.dao.RoomInventoryMongoDao
import com.stayops.room.infrastructure.persistence.dao.RoomMongoDao
import com.stayops.room.infrastructure.persistence.RoomDocument
import com.stayops.room.domain.model.RoomStatus
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.shared.config.FixedTestClockConfig
import com.stayops.shared.domain.MutableClock
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
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@Import(TestcontainersConfiguration::class, FixedTestClockConfig::class)
class RoomInventoryApiTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val inventoryMongoDao: RoomInventoryMongoDao,
    private val roomMongoDao: RoomMongoDao,
    private val inventoryApplication: RoomInventoryApplication,
    private val redisTemplate: StringRedisTemplate,
    private val clock: Clock
) {
    private lateinit var mockMvc: MockMvc

    private val pid = "prop-1"
    private val roomTypeId = "rt-1"
    private lateinit var today: LocalDate
    private val baseUrl = "/api/v1/properties/$pid"

    @BeforeEach
    fun setUp() {
        (clock as MutableClock).set(FixedTestClockConfig.DEFAULT_INSTANT)
        today = LocalDate.now(clock)

        val admin = Member.create(
            id = "test-admin", email = "admin@test.com",
            passwordHash = "hashed", name = "테스트관리자", role = MemberRole.ADMIN
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(admin, null, emptyList())
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        inventoryMongoDao.deleteAll()
        roomMongoDao.deleteAll()
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()

        // 테스트용 객실 3개 생성 + 재고 자동 생성 (기본 마감 — blockedCount=3)
        val now = Instant.now(clock)
        listOf("101", "102", "103").forEach { num ->
            roomMongoDao.save(
                RoomDocument(
                    id = "room-$num", propertyId = pid, roomTypeId = roomTypeId,
                    roomNumber = num, floor = 1, status = RoomStatus.AVAILABLE,
                    memo = null, version = 0L, createdAt = now, updatedAt = now
                )
            )
        }
        inventoryApplication.syncInventoryForRoomType(pid, roomTypeId)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    inner class `GET 가용성 조회` {
        @Test
        fun `날짜 범위의 재고 목록을 반환한다`() {
            mockMvc.get("$baseUrl/availability") {
                param("roomTypeId", roomTypeId)
                param("startDate", today.toString())
                param("endDate", today.plusDays(1).toString())
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].totalCount") { value(3) }
                jsonPath("$[0].availableCount") { value(0) }
                jsonPath("$[0].blockedCount") { value(3) }
            }
        }
    }

    @Nested
    inner class `PUT 재고 차단 및 해제` {
        @Test
        fun `BLOCK 요청이면 차단 후 변경된 재고를 반환한다`() {
            // 기본 마감 상태에서 먼저 전부 오픈
            mockMvc.put("$baseUrl/inventory/$roomTypeId/$today") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateInventoryRequest(InventoryUpdateAction.UNBLOCK, 3))
            }

            // 2개 차단
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
            // 기본 마감 상태 (blockedCount=3)에서 2개 오픈
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
