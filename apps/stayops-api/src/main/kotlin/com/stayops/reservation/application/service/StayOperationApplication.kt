package com.stayops.reservation.application.service

import com.stayops.reservation.domain.event.ReservationCheckedOut
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.RoomStatus
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class StayOperationApplication(
    private val reservationRepository: ReservationRepository,
    private val roomRepository: RoomRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun checkInReservation(propertyId: String, reservationId: String, roomId: String): Reservation {
        val reservation = getReservation(propertyId, reservationId)
        requireArrivalDateToday(reservation, "CHECK_IN_NOT_ALLOWED_DATE", "체크인 당일에만 체크인할 수 있습니다.")

        val room = roomRepository.findById(roomId)
            ?: throw NotFoundException("ROOM_NOT_FOUND", "객실을 찾을 수 없습니다: $roomId")
        check(room.status == RoomStatus.AVAILABLE) {
            "AVAILABLE 상태의 객실만 배정할 수 있습니다: ${room.status}"
        }

        roomRepository.save(room.checkIn())
        val saved = reservationRepository.save(reservation.checkIn(roomId))

        log.info("체크인: reservationId={}, roomId={}", saved.id, roomId)
        return saved
    }

    @Transactional
    fun checkOutReservation(propertyId: String, reservationId: String): Reservation {
        val reservation = getReservation(propertyId, reservationId)
        val saved = reservationRepository.save(reservation.checkOut())

        reservation.roomId
            ?.let { roomRepository.findById(it) }
            ?.also { roomRepository.save(it.checkOut()) }

        eventPublisher.publishEvent(
            ReservationCheckedOut(
                reservationId = saved.id,
                propertyId = reservation.propertyId,
                guestId = reservation.guestId,
                totalAmount = reservation.pricing.totalAmount,
                stayNights = reservation.nightCount.toLong()
            )
        )

        log.info("체크아웃: reservationId={}", saved.id)
        return saved
    }

    fun noShowReservation(propertyId: String, reservationId: String): Reservation {
        val reservation = getReservation(propertyId, reservationId)
        requireArrivalDateToday(reservation, "NO_SHOW_NOT_ALLOWED_DATE", "체크인 당일에만 노쇼 처리할 수 있습니다.")
        return reservationRepository.save(reservation.noShow())
    }

    private fun getReservation(propertyId: String, reservationId: String): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        require(reservation.propertyId == propertyId) { "예약이 해당 숙소에 속하지 않습니다." }
        return reservation
    }

    private fun requireArrivalDateToday(reservation: Reservation, code: String, message: String) {
        val today = LocalDate.now(clock)
        if (reservation.dateRange.checkIn != today) {
            throw BusinessException(
                code = code,
                message = "$message checkIn=${reservation.dateRange.checkIn}, today=$today"
            )
        }
    }
}
