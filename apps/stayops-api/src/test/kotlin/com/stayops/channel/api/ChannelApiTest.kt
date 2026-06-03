package com.stayops.channel.api

import com.stayops.channel.application.service.ChannelApplication
import com.stayops.channel.domain.service.MockOtaRandomBookingResult
import com.stayops.member.infrastructure.security.PropertyAccessChecker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ChannelApiTest {

    private val channelApplication = mockk<ChannelApplication>()
    private val propertyAccessChecker = mockk<PropertyAccessChecker>(relaxed = true)
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ChannelApi(channelApplication, propertyAccessChecker))
        .build()

    @Test
    fun `OTA 랜덤 예약 시뮬레이션 요청은 접근 권한 확인 후 application에 위임한다`() {
        every {
            channelApplication.simulateRandomBooking("prop-1", "ch-1")
        } returns MockOtaRandomBookingResult(
            status = "sent",
            bookingId = "booking-1",
            roomTypeId = "rt-1",
            date = "2026-05-01",
            guestName = "김민수"
        )

        mockMvc.post("/api/v1/properties/prop-1/channels/ch-1/simulate-random-booking")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("sent") }
                jsonPath("$.bookingId") { value("booking-1") }
                jsonPath("$.roomTypeId") { value("rt-1") }
                jsonPath("$.date") { value("2026-05-01") }
                jsonPath("$.guestName") { value("김민수") }
            }

        verify { propertyAccessChecker.requireAccess("prop-1") }
        verify { channelApplication.simulateRandomBooking("prop-1", "ch-1") }
    }
}
