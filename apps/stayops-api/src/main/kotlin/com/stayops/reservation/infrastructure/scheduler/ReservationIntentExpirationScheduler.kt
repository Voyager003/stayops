package com.stayops.reservation.infrastructure.scheduler

import com.stayops.reservation.application.service.ReservationIntentExpirationApplication
import com.stayops.shared.scheduler.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

@Component
class ReservationIntentExpirationScheduler(
    private val expirationApplication: ReservationIntentExpirationApplication,
    private val schedulerLock: SchedulerLock,
    private val clock: Clock,
    @Value("\${stayops.instance-id:\${HOSTNAME:local}}")
    private val instanceId: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${stayops.reservation-intent-expiration.fixed-delay-ms:30000}",
        initialDelayString = "\${stayops.reservation-intent-expiration.initial-delay-ms:30000}"
    )
    fun expirePaymentWaitingIntents() {
        val lockName = "reservation-intent-expiration"
        val owner = "$lockName-$instanceId"
        val now = clock.instant()
        val lockedUntil = now.plus(Duration.ofMinutes(2))

        if (!schedulerLock.tryAcquire(lockName, owner, lockedUntil, now)) {
            log.debug("예약 intent 만료 처리 건너뜀: 다른 인스턴스가 실행 중입니다. owner={}", owner)
            return
        }

        try {
            val expiredCount = expirationApplication.expirePaymentWaitingIntents()
            if (expiredCount > 0) {
                log.info("예약 intent 만료 처리 완료: count={}", expiredCount)
            }
        } finally {
            schedulerLock.release(lockName, owner)
        }
    }
}
