package com.stayops.reservation.application.service

import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.PagedResult
import com.stayops.shared.exception.ForbiddenException
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomerReservationQueryApplication(
    private val reservationRepository: ReservationRepository,
    private val reservationPaymentPort: ReservationPaymentService
) {

    @Transactional(readOnly = true)
    fun getMyReservations(memberId: String, page: Int = 0, size: Int = 20): PagedResult<CustomerReservationReadResult> {
        val reservations = reservationRepository.findPageByMemberId(memberId, page, size)
        val reservationIds = reservations.content.map { it.id }
        val paymentsByReservationId = reservationPaymentPort.findByReservationIds(reservationIds)
            .associateBy { it.reservationId }
        return PagedResult(
            content = reservations.content.map { reservation ->
                CustomerReservationReadResult(reservation, paymentsByReservationId[reservation.id])
            },
            totalElements = reservations.totalElements,
            page = reservations.page,
            size = reservations.size,
            totalPages = reservations.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun getMyReservation(memberId: String, reservationId: String): CustomerReservationReadResult {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        if (reservation.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약만 조회할 수 있습니다")
        }
        return CustomerReservationReadResult(
            reservation = reservation,
            payment = reservationPaymentPort.findByReservationId(reservationId)
        )
    }
}
