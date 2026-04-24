package com.stayops.reservation.api.customer.dto

import com.stayops.reservation.application.port.ReservationPaymentStatus
import com.stayops.reservation.domain.model.ReservationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReservationConfirmationStatusTest {

    @Test
    fun `결제 승인 요청 중이면 확정 처리 중으로 매핑한다`() {
        val status = ReservationConfirmationStatus.from(
            reservationStatus = ReservationStatus.PENDING,
            paymentStatus = ReservationPaymentStatus.CONFIRM_REQUESTED
        )

        assertEquals(ReservationConfirmationStatus.CONFIRMING, status)
    }

    @Test
    fun `예약 확정과 결제 승인이 모두 끝나면 확정 완료로 매핑한다`() {
        val status = ReservationConfirmationStatus.from(
            reservationStatus = ReservationStatus.CONFIRMED,
            paymentStatus = ReservationPaymentStatus.APPROVED
        )

        assertEquals(ReservationConfirmationStatus.CONFIRMED, status)
    }

    @Test
    fun `결제만 완료되고 예약이 아직 대기 중이면 결제 완료 상태로 매핑한다`() {
        val status = ReservationConfirmationStatus.from(
            reservationStatus = ReservationStatus.PENDING,
            paymentStatus = ReservationPaymentStatus.APPROVED
        )

        assertEquals("PAYMENT_COMPLETED", status.name)
    }

    @Test
    fun `예약이 아직 대기 중이고 결제가 실패하면 확정 실패로 매핑한다`() {
        val status = ReservationConfirmationStatus.from(
            reservationStatus = ReservationStatus.PENDING,
            paymentStatus = ReservationPaymentStatus.FAILED
        )

        assertEquals(ReservationConfirmationStatus.FAILED, status)
    }

    @Test
    fun `결제 전 예약 취소로 결제 실패 처리된 경우 취소 완료로 매핑한다`() {
        val status = ReservationConfirmationStatus.from(
            reservationStatus = ReservationStatus.CANCELLED,
            paymentStatus = ReservationPaymentStatus.FAILED
        )

        assertEquals(ReservationConfirmationStatus.CANCELLED, status)
    }

    @Test
    fun `환불 실패는 수동 확인 필요로 매핑한다`() {
        val status = ReservationConfirmationStatus.from(
            reservationStatus = ReservationStatus.CANCELLED,
            paymentStatus = ReservationPaymentStatus.CANCEL_FAILED
        )

        assertEquals(ReservationConfirmationStatus.MANUAL_REVIEW_REQUIRED, status)
    }
}
