package com.stayops.mockota.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class FailureSimulatorService {

    private val failureModes = ConcurrentHashMap<String, FailureMode>()

    fun setFailureMode(mode: FailureMode) {
        failureModes[mode.type] = mode
    }

    fun clearAll() {
        failureModes.clear()
    }

    fun getActiveMode(): FailureMode? =
        failureModes.values.firstOrNull()

    fun shouldFail(): Boolean =
        failureModes.isNotEmpty()
}

data class FailureMode(
    val type: String,
    val delayMs: Long = 0
) {
    companion object {
        fun timeout(delayMs: Long = 30_000) = FailureMode(type = "TIMEOUT", delayMs = delayMs)
        fun serverError() = FailureMode(type = "SERVER_ERROR")
        fun rateLimit() = FailureMode(type = "RATE_LIMIT")
    }
}
