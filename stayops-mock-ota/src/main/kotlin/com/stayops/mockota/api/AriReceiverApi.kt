package com.stayops.mockota.api

import com.stayops.mockota.model.ReceivedAri
import com.stayops.mockota.service.FailureSimulatorService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.CopyOnWriteArrayList

@RestController
@RequestMapping("/api/v1/ari")
class AriReceiverApi(
    private val failureSimulator: FailureSimulatorService
) {

    private val receivedAriList = CopyOnWriteArrayList<ReceivedAri>()

    @PostMapping("/availability")
    fun receiveAvailability(
        @RequestBody payload: Map<String, Any>,
        @RequestHeader("X-Idempotency-Key", required = false) idempotencyKey: String?
    ): ResponseEntity<Map<String, Any>> {
        val activeMode = failureSimulator.getActiveMode()
        if (activeMode != null) {
            return when (activeMode.type) {
                "TIMEOUT" -> {
                    Thread.sleep(activeMode.delayMs)
                    ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(mapOf("error" to "timeout"))
                }
                "SERVER_ERROR" -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(mapOf("error" to "service unavailable"))
                "RATE_LIMIT" -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(mapOf("error" to "rate limit exceeded"))
                else -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("error" to "unknown failure"))
            }
        }

        val key = idempotencyKey ?: ""
        if (receivedAriList.any { it.idempotencyKey == key && key.isNotEmpty() }) {
            return ResponseEntity.ok(mapOf("status" to "duplicate", "idempotencyKey" to key))
        }

        val ari = ReceivedAri(
            roomTypeCode = payload["roomTypeCode"]?.toString() ?: "",
            date = payload["date"]?.toString() ?: "",
            availableCount = (payload["availableCount"] as? Number)?.toInt() ?: 0,
            idempotencyKey = key
        )
        receivedAriList.add(ari)

        return ResponseEntity.ok(mapOf("status" to "accepted", "idempotencyKey" to key))
    }

    @GetMapping("/received")
    fun getReceivedAri(): List<ReceivedAri> = receivedAriList.toList()

    @PostMapping("/clear")
    fun clearReceived() {
        receivedAriList.clear()
    }
}
