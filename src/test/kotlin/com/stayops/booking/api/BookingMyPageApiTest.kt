package com.stayops.booking.api

import com.stayops.booking.application.service.BookingApplication
import com.stayops.booking.application.service.BookingResult
import com.stayops.payment.domain.model.Payment
import com.stayops.reservation.domain.model.*
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.GlobalExceptionHandler
import com.stayops.member.infrastructure.security.CustomerAuthChecker
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.LocalDate

class BookingMyPageApiTest {

    private val bookingApplication = mockk<BookingApplication>()
    private val customerAuthChecker = mockk<CustomerAuthChecker>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(BookingMyPageApi(bookingApplication, customerAuthChecker))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    private fun mockCustomer() {
        every { customerAuthChecker.requireCustomer() } returns com.stayops.member.domain.model.Member.create(
            id = "member-1", email = "customer@test.com",
            passwordHash = "hashed", name = "김고객",
            role = com.stayops.member.domain.model.MemberRole.CUSTOMER
        )
    }

    private fun sampleReservation(id: String = "rsv-1") = Reservation.create(
        id = id, propertyId = "prop-1", roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = GuestInfo("김고객", "010-1111-2222", null),
        dateRange = DateRange.of(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3)),
        numberOfGuests = 2,
        channel = BookingChannel("DIRECT", commissionRate = BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
        memberId = "member-1"
    )

    @Nested
    inner class `내_예약_목록` {

        @Test
        fun `예약 목록을 반환한다`() {
            mockCustomer()
            every { bookingApplication.getMyReservations("member-1") } returns listOf(
                sampleReservation("rsv-1"),
                sampleReservation("rsv-2")
            )

            mockMvc.get("/api/v1/booking/my/reservations")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(2) }
                    jsonPath("$[0].reservationId") { value("rsv-1") }
                }
        }
    }

    @Nested
    inner class `예약_상세` {

        @Test
        fun `예약 상세를 반환한다`() {
            mockCustomer()
            every { bookingApplication.getMyReservation("member-1", "rsv-1") } returns sampleReservation()

            mockMvc.get("/api/v1/booking/my/reservations/rsv-1")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.reservationId") { value("rsv-1") }
                    jsonPath("$.status") { value("PENDING") }
                }
        }
    }

    @Nested
    inner class `예약_취소` {

        @Test
        fun `취소 후 결과를 반환한다`() {
            mockCustomer()
            val cancelled = sampleReservation().confirm().cancel()
            val payment = Payment.create(
                id = "pay-1", reservationId = "rsv-1",
                memberId = "member-1", amount = Money.won(200_000)
            ).approve(paymentKey = "pk", method = "카드", approvedAt = java.time.Instant.now()).cancel()

            every { bookingApplication.cancelBooking("member-1", "rsv-1") } returns BookingResult(cancelled, payment)

            mockMvc.post("/api/v1/booking/my/reservations/rsv-1/cancel")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.reservationStatus") { value("CANCELLED") }
                    jsonPath("$.paymentStatus") { value("CANCELLED") }
                }
        }
    }
}
