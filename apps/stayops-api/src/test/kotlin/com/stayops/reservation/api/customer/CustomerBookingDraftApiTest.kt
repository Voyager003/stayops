package com.stayops.reservation.api.customer

import com.stayops.reservation.application.service.CustomerBookingDraft
import com.stayops.reservation.application.service.CustomerBookingDraftApplication
import com.stayops.shared.exception.GlobalExceptionHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.LocalDate

class CustomerBookingDraftApiTest {

    private val bookingDraftApplication = mockk<CustomerBookingDraftApplication>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(CustomerBookingDraftApi(bookingDraftApplication))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @Test
    fun `예약 draft를 생성하면 draftId와 선택 정보를 반환한다`() {
        every {
            bookingDraftApplication.createDraft(
                propertyId = "prop-1",
                roomTypeId = "rt-1",
                checkIn = LocalDate.of(2026, 6, 10),
                checkOut = LocalDate.of(2026, 6, 12),
                guests = 2
            )
        } returns sampleDraft()

        mockMvc.post("/api/v1/customer/booking-drafts") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "propertyId": "prop-1",
                  "roomTypeId": "rt-1",
                  "checkIn": "2026-06-10",
                  "checkOut": "2026-06-12",
                  "guests": 2
                }
            """.trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.draftId") { value("draft-1") }
            jsonPath("$.propertyId") { value("prop-1") }
            jsonPath("$.roomTypeId") { value("rt-1") }
            jsonPath("$.checkIn") { value("2026-06-10") }
            jsonPath("$.checkOut") { value("2026-06-12") }
            jsonPath("$.guests") { value(2) }
        }
    }

    @Test
    fun `예약 draft를 조회하면 선택 정보를 반환한다`() {
        every { bookingDraftApplication.getDraft("draft-1") } returns sampleDraft()

        mockMvc.get("/api/v1/customer/booking-drafts/draft-1")
            .andExpect {
                status { isOk() }
                jsonPath("$.draftId") { value("draft-1") }
                jsonPath("$.propertyId") { value("prop-1") }
            }
    }

    @Test
    fun `없는 예약 draft를 조회하면 404를 반환한다`() {
        every { bookingDraftApplication.getDraft("missing") } returns null

        mockMvc.get("/api/v1/customer/booking-drafts/missing")
            .andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `예약 draft를 삭제한다`() {
        every { bookingDraftApplication.deleteDraft("draft-1") } returns Unit

        mockMvc.delete("/api/v1/customer/booking-drafts/draft-1")
            .andExpect {
                status { isNoContent() }
            }

        verify { bookingDraftApplication.deleteDraft("draft-1") }
    }

    private fun sampleDraft(): CustomerBookingDraft =
        CustomerBookingDraft(
            draftId = "draft-1",
            propertyId = "prop-1",
            roomTypeId = "rt-1",
            checkIn = LocalDate.of(2026, 6, 10),
            checkOut = LocalDate.of(2026, 6, 12),
            guests = 2
        )
}
