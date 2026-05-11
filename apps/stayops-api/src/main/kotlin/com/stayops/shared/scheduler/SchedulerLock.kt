package com.stayops.shared.scheduler

import java.time.Instant

interface SchedulerLock {
    fun tryAcquire(name: String, owner: String, lockedUntil: Instant, now: Instant = Instant.now()): Boolean
    fun release(name: String, owner: String)
}
