package com.stayops.payment.infrastructure.scheduler

import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.reservation.domain.repository.ReservationRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class PendingReservationScheduler(
    private val reservationRepository: ReservationRepository,
    private val paymentRepository: PaymentRepository,
    private val inventoryApplication: RoomInventoryApplication
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 60_000)
    fun expirePendingReservations() {
        val now = Instant.now()
        val expiredReservations = reservationRepository.findExpiredPending(now)

        if (expiredReservations.isEmpty()) return

        log.info("만료된 PENDING 예약 {}건 처리 시작", expiredReservations.size)

        expiredReservations.forEach { reservation ->
            try {
                // 1. Reservation 취소
                reservationRepository.save(reservation.cancelPending())

                // 2. Payment 실패 처리
                val payment = paymentRepository.findByReservationId(reservation.id)
                if (payment != null) {
                    paymentRepository.save(payment.fail("결제 시간 초과"))
                }

                // 3. 재고 복원
                reservation.dateRange.allDates().forEach { date ->
                    inventoryApplication.release(reservation.propertyId, reservation.roomTypeId, date)
                }

                log.info("PENDING 예약 만료 처리 완료: reservationId={}", reservation.id)
            } catch (e: Exception) {
                log.error("PENDING 예약 만료 처리 실패: reservationId={}", reservation.id, e)
            }
        }
    }
}
