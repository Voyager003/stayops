package com.stayops.dashboard.api

import com.stayops.dashboard.application.dto.DashboardSummary
import com.stayops.dashboard.application.service.DashboardApplication
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.PropertyRole
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DashboardApiTest {

    private val dashboardApplication = mockk<DashboardApplication>()
    private val memberAccessApplication = mockk<MemberAccessApplication>(relaxed = true)
    private val fixedClock = Clock.fixed(Instant.parse("2026-04-08T10:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(DashboardApi(dashboardApplication, memberAccessApplication, fixedClock))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .build()

    private fun sampleSummary() = DashboardSummary(
        todayCheckInCount = 3, todayCheckOutCount = 2,
        todayRevenue = 350_000, todayNewReservations = 1,
        yesterdayCheckInCount = 2, yesterdayCheckOutCount = 1,
        yesterdayRevenue = 200_000, yesterdayNewReservations = 2,
        pendingReservations = 0,
        occupancy = DashboardSummary.OccupancySummary(10, 6, 4, 60.0)
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
            every { dashboardApplication.getDashboard("prop-1", any<LocalDate>()) } returns sampleSummary()

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
            every { memberAccessApplication.resolveAccessiblePropertyIds(any()) } returns listOf("prop-1", "prop-2")

            every {
                dashboardApplication.getAggregatedDashboard(listOf("prop-1", "prop-2"), any<LocalDate>())
            } returns sampleSummary()

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
            every { memberAccessApplication.resolveAccessiblePropertyIds(any()) } returns listOf("prop-1", "prop-2")
            every {
                dashboardApplication.getAggregatedDashboard(listOf("prop-1", "prop-2"), any<LocalDate>())
            } returns sampleSummary()

            mockMvc.get("/api/v1/dashboard")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.todayRevenue") { value(350_000) }
                }

            verify { memberAccessApplication.resolveAccessiblePropertyIds(any()) }
        }
    }
}
