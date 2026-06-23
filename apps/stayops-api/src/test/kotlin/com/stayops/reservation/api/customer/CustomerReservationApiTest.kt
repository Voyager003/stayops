package com.stayops.reservation.api.customer

import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.reservation.application.service.CustomerReservationResult
import com.stayops.reservation.application.required.ReservationPaymentSnapshot
import com.stayops.reservation.application.required.ReservationPaymentStatus
import com.stayops.reservation.domain.model.*
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.GlobalExceptionHandler
import com.stayops.shared.exception.NotFoundException
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
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.math.BigDecimal
import java.time.LocalDate

class CustomerReservationApiTest {

    private val customerReservationApplication = mockk<CustomerReservationApplication>()
    private val memberAccessApplication = mockk<MemberAccessApplication>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(CustomerReservationApi(customerReservationApplication, memberAccessApplication))
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

    private fun sampleCustomerReservationResult(): CustomerReservationResult {
        val reservation = Reservation.create(
            id = "rsv-1", propertyId = "prop-1", roomTypeId = "rt-1",
            guestId = "guest-1",
            guestInfo = GuestInfo("김고객", "010-1111-2222", null),
            dateRange = DateRange.of(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3)),
            numberOfGuests = 2,
            channel = ReservationChannel("DIRECT", commissionRate = BigDecimal.ZERO),
            pricing = ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO),
            memberId = "member-1"
        )
        val payment = ReservationPaymentSnapshot(
            id = "pay-1",
            reservationId = "rsv-1",
            memberId = "member-1",
            orderId = "STAYOPS-rsv-1-123",
            amount = Money.won(200_000),
            status = ReservationPaymentStatus.PENDING,
            paymentKey = null,
            failReason = null
        )
        return CustomerReservationResult(reservation, payment)
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

    @Nested
    inner class `예약_생성` {

        @Test
        fun `유효한 요청이면 201을 반환한다`() {
            mockCustomer()
            every { customerReservationApplication.createReservation(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns sampleCustomerReservationResult()

            mockMvc.post("/api/v1/customer/reservations") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                        "propertyId": "prop-1",
                        "roomTypeId": "rt-1",
                        "checkIn": "2026-04-01",
                        "checkOut": "2026-04-03",
                        "numberOfGuests": 2,
                        "guestName": "김고객",
                        "guestPhone": "010-1111-2222",
                        "guestEmail": "kim@test.com"
                    }
                """.trimIndent()
            }.andExpect {
                status { isCreated() }
                jsonPath("$.reservationId") { value("rsv-1") }
                jsonPath("$.paymentStatus") { value("PENDING") }
                jsonPath("$.confirmationStatus") { value("PAYMENT_WAITING") }
                jsonPath("$.orderId") { exists() }
            }
        }
    }

    @Nested
    inner class `결제_확인` {

        @Test
        fun `유효한 요청이면 202를 반환한다`() {
            mockCustomer()
            val result = sampleCustomerReservationResult()
            val requestedPayment = result.payment.copy(
                status = ReservationPaymentStatus.CONFIRM_REQUESTED,
                paymentKey = "toss_pk_123"
            )
            every { customerReservationApplication.confirmPayment(any(), any(), any(), any(), any()) } returns
                CustomerReservationResult(result.reservation, requestedPayment)

            mockMvc.post("/api/v1/customer/reservations/rsv-1/confirm-payment") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                        "paymentKey": "toss_pk_123",
                        "orderId": "STAYOPS-rsv-1-123",
                        "amount": 200000
                    }
                """.trimIndent()
            }.andExpect {
                status { isAccepted() }
                jsonPath("$.reservationStatus") { value("PENDING") }
                jsonPath("$.paymentStatus") { value("CONFIRM_REQUESTED") }
                jsonPath("$.confirmationStatus") { value("CONFIRMING") }
            }
        }
    }
}
