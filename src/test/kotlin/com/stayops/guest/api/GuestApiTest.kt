package com.stayops.guest.api

import com.stayops.TestcontainersConfiguration
import com.stayops.guest.api.dto.UpdateGuestRequest
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.infrastructure.persistence.GuestDocument
import com.stayops.guest.infrastructure.persistence.GuestMongoDataRepository
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
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class GuestApiTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val objectMapper: ObjectMapper,
    private val mongoDataRepository: GuestMongoDataRepository
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

    private fun saveGuest(
        id: String = "guest-1",
        name: String = "홍길동",
        phone: String = "010-1234-5678",
    ): Guest {
        val guest = Guest.create(id = id, propertyId = pid, name = name, phone = phone)
        mongoDataRepository.save(GuestDocument.from(guest))
        return guest
    }

    @Nested
    inner class `GET 단건 조회` {
        @Test
        fun `존재하는 Guest를 조회하면 200과 정보를 반환한다`() {
            saveGuest()

            mockMvc.get("$baseUrl/guests/guest-1")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.name") { value("홍길동") }
                    jsonPath("$.phone") { value("010-1234-5678") }
                    jsonPath("$.tier") { value("NEW") }
                    jsonPath("$.totalVisits") { value(0) }
                }
        }

        @Test
        fun `존재하지 않는 Guest를 조회하면 404를 반환한다`() {
            mockMvc.get("$baseUrl/guests/unknown")
                .andExpect {
                    status { isNotFound() }
                }
        }
    }

    @Nested
    inner class `GET 목록 조회` {
        @Test
        fun `숙소의 전체 Guest 목록을 반환한다`() {
            saveGuest(id = "guest-1", name = "홍길동", phone = "010-1111-1111")
            saveGuest(id = "guest-2", name = "김철수", phone = "010-2222-2222")

            mockMvc.get("$baseUrl/guests")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(2) }
                }
        }

        @Test
        fun `이름으로 필터링한다`() {
            saveGuest(id = "guest-1", name = "홍길동", phone = "010-1111-1111")
            saveGuest(id = "guest-2", name = "김철수", phone = "010-2222-2222")

            mockMvc.get("$baseUrl/guests?name=홍")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(1) }
                    jsonPath("$[0].name") { value("홍길동") }
                }
        }

        @Test
        fun `등급으로 필터링한다`() {
            saveGuest(id = "guest-1", name = "홍길동", phone = "010-1111-1111")

            mockMvc.get("$baseUrl/guests?tier=NEW")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(1) }
                }
        }
    }

    @Nested
    inner class `PUT 정보 수정` {
        @Test
        fun `유효한 정보로 수정하면 200과 변경된 정보를 반환한다`() {
            saveGuest()

            mockMvc.put("$baseUrl/guests/guest-1") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateGuestRequest(name = "김철수", memo = "VIP 고객"))
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("김철수") }
                jsonPath("$.memo") { value("VIP 고객") }
            }
        }

        @Test
        fun `이름이 공백이면 400을 반환한다`() {
            saveGuest()

            mockMvc.put("$baseUrl/guests/guest-1") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateGuestRequest(name = " ", memo = null))
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `존재하지 않는 Guest를 수정하면 404를 반환한다`() {
            mockMvc.put("$baseUrl/guests/unknown") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(UpdateGuestRequest(name = "김철수", memo = null))
            }.andExpect {
                status { isNotFound() }
            }
        }
    }
}
