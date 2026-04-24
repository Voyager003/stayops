package com.stayops.reservation.api.customer

import com.stayops.reservation.application.service.CustomerReservationApplication
import com.stayops.reservation.application.service.CustomerReservationReadResult
import com.stayops.reservation.application.service.CustomerReservationResult
import com.stayops.reservation.application.port.ReservationPaymentSnapshot
import com.stayops.reservation.application.port.ReservationPaymentStatus
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

class CustomerReservationMyPageApiTest {

    private val customerReservationApplication = mockk<CustomerReservationApplication>()
    private val customerAuthChecker = mockk<CustomerAuthChecker>()
    private val mockMvc: MockMvc = MockMvcBuilders
        .standaloneSetup(CustomerReservationMyPageApi(customerReservationApplication, customerAuthChecker))
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
        fun `예약 목록을 반환한다`() {
            mockCustomer()
            every { customerReservationApplication.getMyReservations("member-1") } returns listOf(
                CustomerReservationReadResult(sampleReservation("rsv-1"), samplePayment("rsv-1")),
                CustomerReservationReadResult(
                    sampleReservation("rsv-2").confirm(),
                    samplePayment("rsv-2").copy(status = ReservationPaymentStatus.APPROVED, paymentKey = "pk-2")
                )
            )

            mockMvc.get("/api/v1/customer/reservations")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.length()") { value(2) }
                    jsonPath("$[0].reservationId") { value("rsv-1") }
                    jsonPath("$[0].paymentStatus") { value("PENDING") }
                    jsonPath("$[0].confirmationStatus") { value("PAYMENT_WAITING") }
                    jsonPath("$[1].status") { value("CONFIRMED") }
                    jsonPath("$[1].paymentStatus") { value("APPROVED") }
                    jsonPath("$[1].confirmationStatus") { value("CONFIRMED") }
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
