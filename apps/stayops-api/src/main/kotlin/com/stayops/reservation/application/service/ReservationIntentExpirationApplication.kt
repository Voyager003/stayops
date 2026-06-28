package com.stayops.reservation.application.service

import com.stayops.inventory.application.provided.InventoryHoldService
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class ReservationIntentExpirationApplication(
    private val reservationIntentRepository: ReservationIntentRepository,
    private val inventoryHoldService: InventoryHoldService,
    private val clock: Clock
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun expirePaymentWaitingIntents(limit: Int = 100): Int {
        val now = clock.instant()
        val expiredIntents = reservationIntentRepository.findExpiredPaymentWaiting(now, limit)
        expiredIntents.forEach { intent ->
            inventoryHoldService.release(intent.id)
            reservationIntentRepository.save(intent.expire(now))
            log.info("예약 intent 만료 처리: reservationIntentId={}, holdId={}", intent.id, intent.holdId)
        }
        return expiredIntents.size
    }
}
