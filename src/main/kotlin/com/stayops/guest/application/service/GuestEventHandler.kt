package com.stayops.guest.application.service

import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.reservation.domain.event.ReservationCheckedOut
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class GuestEventHandler(
    private val guestRepository: GuestRepository,
    private val clock: Clock
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onReservationCheckedOut(event: ReservationCheckedOut) {
        val guest = guestRepository.findById(event.guestId)
        if (guest == null) {
            log.warn("체크아웃 이벤트 처리 실패 — 고객을 찾을 수 없습니다: guestId={}", event.guestId)
            return
        }

        val updated = guest.recordVisit(
            spend = event.totalAmount,
            stayNights = event.stayNights.toInt(),
            visitDate = LocalDate.now(clock)
        )
        guestRepository.save(updated)

        log.info(
            "고객 방문 이력 갱신: guestId={}, tier={}, totalVisits={}",
            updated.id, updated.tier, updated.visitSummary.totalVisits
        )
    }
}
