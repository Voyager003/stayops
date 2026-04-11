package com.stayops.settlement.api

import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.settlement.application.dto.ChannelSettlement
import com.stayops.settlement.application.dto.SettlementSummary
import com.stayops.settlement.application.service.SettlementQueryApplication
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.GlobalExceptionHandler
import com.stayops.shared.security.PropertyAccessChecker
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate

class SettlementApiTest {

    private val settlementQueryApplication = mockk<SettlementQueryApplication>()
    private val propertyAccessChecker = mockk<PropertyAccessChecker>(relaxed = true)
    private val propertyRepository = mockk<PropertyRepository>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(SettlementApi(settlementQueryApplication, propertyAccessChecker, propertyRepository))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    private val startDate = LocalDate.of(2026, 4, 1)
    private val endDate = LocalDate.of(2026, 4, 30)

    @Nested
    inner class `정산_조회_API` {

        @Test
        fun `채널별 정산 요약을 반환한다`() {
            val summary = SettlementSummary(
                startDate = startDate,
                endDate = endDate,
                totalReservations = 3,
                totalRevenue = Money.won(600_000),
                totalCommission = Money.won(30_000),
                netSettlement = Money.won(570_000),
                documentCount = 3,
                byChannel = listOf(
                    ChannelSettlement("DIRECT", 2, Money.won(400_000), Money.won(0), Money.won(400_000)),
                    ChannelSettlement("AGODA", 1, Money.won(200_000), Money.won(30_000), Money.won(170_000))
                )
            )
            every { settlementQueryApplication.getSettlementSummary("prop-1", startDate, endDate) } returns summary

            mockMvc.get("/api/v1/properties/prop-1/settlements") {
                param("startDate", "2026-04-01")
                param("endDate", "2026-04-30")
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalReservations") { value(3) }
                jsonPath("$.totalRevenue") { value(600_000) }
                jsonPath("$.totalCommission") { value(30_000) }
                jsonPath("$.netSettlement") { value(570_000) }
                jsonPath("$.byChannel.length()") { value(2) }
            }
        }

        @Test
        fun `정산 대상이 없으면 모든 값이 0이다`() {
            val emptySummary = SettlementSummary(
                startDate = startDate,
                endDate = endDate,
                totalReservations = 0,
                totalRevenue = Money.ZERO,
                totalCommission = Money.ZERO,
                netSettlement = Money.ZERO,
                documentCount = 0,
                byChannel = emptyList()
            )
            every { settlementQueryApplication.getSettlementSummary("prop-1", startDate, endDate) } returns emptySummary

            mockMvc.get("/api/v1/properties/prop-1/settlements") {
                param("startDate", "2026-04-01")
                param("endDate", "2026-04-30")
            }.andExpect {
                status { isOk() }
                jsonPath("$.totalReservations") { value(0) }
                jsonPath("$.totalRevenue") { value(0) }
                jsonPath("$.totalCommission") { value(0) }
                jsonPath("$.netSettlement") { value(0) }
                jsonPath("$.byChannel.length()") { value(0) }
            }
        }

        @Test
        fun `startDate 또는 endDate 누락 시 400을 반환한다`() {
            mockMvc.get("/api/v1/properties/prop-1/settlements") {
                param("startDate", "2026-04-01")
            }.andExpect {
                status { isBadRequest() }
            }
        }
    }
}
