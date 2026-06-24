package com.stayops.reservation.api.customer

import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.reservation.application.service.CustomerReservationReadResult
import com.stayops.reservation.application.service.CustomerReservationResult
import com.stayops.reservation.application.required.ReservationPaymentSnapshot
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.*
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.PagedResult
import com.stayops.shared.exception.GlobalExceptionHandler
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.LocalDate

class CustomerReservationMyPageApiTest {

    private val customerReservationApplication = mockk<CustomerReservationApplication>()
    private val memberAccessApplication = mockk<MemberAccessApplication>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(CustomerReservationMyPageApi(customerReservationApplication, memberAccessApplication))
        .setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun mockCustomer() {
        val customer = Member.create(
            id = "member-1", email = "customer@test.com",
            passwordHash = "hashed", name = "김고객",
            role = MemberRole.CUSTOMER
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(customer, null, emptyList())
        every { memberAccessApplication.requireCustomer(customer) } returns customer
    }

    private fun sampleReservation(id: String = "rsv-1") = Reservation.create(
        id = id, propertyId = "prop-1", roomTypeId = "rt-1",
        guestId = "guest-1",
        guestInfo = GuestInfo("김고객", "010-1111-2222", null),
        dateRange = DateRange.of(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3)),
        numberOfGuests = 2,
        channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
        pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
        memberId = "member-1"
    )

    private fun samplePayment(reservationId: String = "rsv-1") = ReservationPaymentSnapshot(
        id = "pay-$reservationId",
        reservationId = reservationId,
        memberId = "member-1",
        orderId = "STAYOPS-$reservationId-123",
        amount = Money.won(200_000),
        status = ReservationPaymentStatus.PENDING,
        paymentKey = null,
        failReason = null
    )

    @Nested
    inner class `내_예약_목록` {

        @Test
        fun `예약 목록을 페이지로 반환한다`() {
            mockCustomer()
            every { customerReservationApplication.getMyReservations("member-1", 1, 20) } returns PagedResult(
                content = listOf(
                    CustomerReservationReadResult(sampleReservation("rsv-1"), samplePayment("rsv-1")),
                    CustomerReservationReadResult(
                        sampleReservation("rsv-2").confirm(),
                        samplePayment("rsv-2").copy(status = ReservationPaymentStatus.APPROVED, paymentKey = "pk-2")
                    )
                ),
                totalElements = 42,
                page = 1,
                size = 20,
                totalPages = 3
            )

            mockMvc.get("/api/v1/customer/reservations?page=1&size=20")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.content.length()") { value(2) }
                    jsonPath("$.content[0].reservationId") { value("rsv-1") }
                    jsonPath("$.content[0].paymentStatus") { value("PENDING") }
                    jsonPath("$.content[0].confirmationStatus") { value("PAYMENT_WAITING") }
                    jsonPath("$.content[1].status") { value("CONFIRMED") }
                    jsonPath("$.content[1].paymentStatus") { value("APPROVED") }
                    jsonPath("$.content[1].confirmationStatus") { value("CONFIRMED") }
                    jsonPath("$.totalElements") { value(42) }
                    jsonPath("$.page") { value(1) }
                    jsonPath("$.size") { value(20) }
                    jsonPath("$.totalPages") { value(3) }
                }
        }
    }

    @Nested
    inner class `예약_상세` {

        @Test
        fun `예약 상세를 반환한다`() {
            mockCustomer()
            every { customerReservationApplication.getMyReservation("member-1", "rsv-1") } returns
                CustomerReservationReadResult(
                    sampleReservation(),
                    samplePayment().copy(status = ReservationPaymentStatus.CONFIRM_REQUESTED, paymentKey = "pk-1")
                )

            mockMvc.get("/api/v1/customer/reservations/rsv-1")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.reservationId") { value("rsv-1") }
                    jsonPath("$.status") { value("PENDING") }
                    jsonPath("$.paymentStatus") { value("CONFIRM_REQUESTED") }
                    jsonPath("$.confirmationStatus") { value("CONFIRMING") }
                }
        }

        @Test
        fun `결제는 완료되었지만 예약이 아직 확정 전이면 결제 완료 상태를 반환한다`() {
            mockCustomer()
            every { customerReservationApplication.getMyReservation("member-1", "rsv-1") } returns
                CustomerReservationReadResult(
                    sampleReservation(),
                    samplePayment().copy(status = ReservationPaymentStatus.APPROVED, paymentKey = "pk-1")
                )

            mockMvc.get("/api/v1/customer/reservations/rsv-1")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.reservationId") { value("rsv-1") }
                    jsonPath("$.status") { value("PENDING") }
                    jsonPath("$.paymentStatus") { value("APPROVED") }
                    jsonPath("$.confirmationStatus") { value("PAYMENT_COMPLETED") }
                }
        }
    }

    @Nested
    inner class `예약_취소` {

        @Test
        fun `취소 후 결과를 반환한다`() {
            mockCustomer()
            val cancelled = sampleReservation().confirm().cancel()
            val payment = samplePayment().copy(status = ReservationPaymentStatus.CANCELLED, paymentKey = "pk")

            every { customerReservationApplication.cancelReservation("member-1", "rsv-1") } returns CustomerReservationResult(cancelled, payment)

            mockMvc.post("/api/v1/customer/reservations/rsv-1/cancel")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.reservationStatus") { value("CANCELLED") }
                    jsonPath("$.paymentStatus") { value("CANCELLED") }
                }
        }
    }
}
