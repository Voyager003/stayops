package com.stayops.dashboard.api

import com.stayops.auth.domain.model.Member
import com.stayops.auth.domain.model.MemberRole
import com.stayops.auth.domain.model.PropertyAccess
import com.stayops.auth.domain.model.PropertyRole
import com.stayops.dashboard.api.dto.DashboardResponse
import com.stayops.dashboard.application.service.DashboardApplication
import com.stayops.property.domain.model.*
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.shared.security.PropertyAccessChecker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate

class DashboardApiTest {

    private val dashboardApplication = mockk<DashboardApplication>()
    private val propertyAccessChecker = mockk<PropertyAccessChecker>(relaxed = true)
    private val propertyRepository = mockk<PropertyRepository>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(DashboardApi(dashboardApplication, propertyAccessChecker, propertyRepository))
        .build()

    private fun sampleResponse() = DashboardResponse(
        todayCheckInCount = 3, todayCheckOutCount = 2,
        todayRevenue = 350_000, todayNewReservations = 1,
        yesterdayCheckInCount = 2, yesterdayCheckOutCount = 1,
        yesterdayRevenue = 200_000, yesterdayNewReservations = 2,
        pendingReservations = 0,
        occupancy = DashboardResponse.OccupancyResponse(10, 6, 4, 60.0)
    )

    private fun setAuthentication(member: Member) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(member, null, emptyList())
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    inner class `GET 단일 숙소 대시보드` {
        @Test
        fun `200과 대시보드 데이터를 반환한다`() {
            every { dashboardApplication.getDashboard("prop-1", any<LocalDate>()) } returns sampleResponse()

            mockMvc.get("/api/v1/properties/prop-1/dashboard")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.todayCheckInCount") { value(3) }
                    jsonPath("$.todayCheckOutCount") { value(2) }
                    jsonPath("$.todayRevenue") { value(350_000) }
                    jsonPath("$.todayNewReservations") { value(1) }
                    jsonPath("$.yesterdayCheckInCount") { value(2) }
                    jsonPath("$.yesterdayRevenue") { value(200_000) }
                    jsonPath("$.occupancy.rate") { value(60.0) }
                }
        }
    }

    @Nested
    inner class `GET 모든 숙소 대시보드` {
        @Test
        fun `OWNER가 접근 가능한 숙소의 합산 대시보드를 반환한다`() {
            val owner = Member.create(
                id = "owner-1", email = "owner@test.com",
                passwordHash = "hashed", name = "소유자", role = MemberRole.OWNER
            ).grantAccess("prop-1", PropertyRole.OWNER)
                .grantAccess("prop-2", PropertyRole.OWNER)

            setAuthentication(owner)

            every {
                dashboardApplication.getAggregatedDashboard(listOf("prop-1", "prop-2"), any<LocalDate>())
            } returns sampleResponse()

            mockMvc.get("/api/v1/dashboard")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.todayCheckInCount") { value(3) }
                    jsonPath("$.todayRevenue") { value(350_000) }
                }

            verify { dashboardApplication.getAggregatedDashboard(listOf("prop-1", "prop-2"), any()) }
        }

        @Test
        fun `ADMIN은 전체 숙소의 합산 대시보드를 반환한다`() {
            val admin = Member.create(
                id = "admin-1", email = "admin@test.com",
                passwordHash = "hashed", name = "관리자", role = MemberRole.ADMIN
            )
            setAuthentication(admin)

            val property1 = Property.create("prop-1", "admin-1", "호텔A", PropertyType.HOTEL,
                Address.of("길1", "서울", "서울특별시", "00000", "KR"),
                ContactInfo.of("010-0000-0000", "a@a.com"), "설명")
            val property2 = Property.create("prop-2", "admin-1", "호텔B", PropertyType.HOTEL,
                Address.of("길2", "부산", "부산광역시", "00000", "KR"),
                ContactInfo.of("010-0000-0001", "b@b.com"), "설명")

            every { propertyRepository.findAll() } returns listOf(property1, property2)
            every {
                dashboardApplication.getAggregatedDashboard(listOf("prop-1", "prop-2"), any<LocalDate>())
            } returns sampleResponse()

            mockMvc.get("/api/v1/dashboard")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.todayRevenue") { value(350_000) }
                }

            verify { propertyRepository.findAll() }
        }
    }
}
