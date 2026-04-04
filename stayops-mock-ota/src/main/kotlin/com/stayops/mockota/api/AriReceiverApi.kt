package com.stayops.mockota.api

import com.stayops.mockota.model.OtaInventory
import com.stayops.mockota.repository.OtaInventoryRepository
import com.stayops.mockota.service.FailureSimulatorService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/v1/ari")
class AriReceiverApi(
    private val failureSimulator: FailureSimulatorService,
    private val otaInventoryRepository: OtaInventoryRepository
) {

    private val processedIdempotencyKeys = ConcurrentHashMap.newKeySet<String>()

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
        if (key.isNotEmpty() && !processedIdempotencyKeys.add(key)) {
            return ResponseEntity.ok(mapOf("status" to "duplicate", "idempotencyKey" to key))
        }

        val roomTypeId = payload["roomTypeCode"]?.toString() ?: ""
        val date = payload["date"]?.toString() ?: ""
        val availableCount = (payload["availableCount"] as? Number)?.toInt() ?: 0

        val existing = otaInventoryRepository.findByRoomTypeIdAndDate(roomTypeId, date)
        if (existing != null) {
            otaInventoryRepository.save(existing.copy(availableCount = availableCount, updatedAt = Instant.now()))
        } else {
            otaInventoryRepository.save(
                OtaInventory(roomTypeId = roomTypeId, date = date, availableCount = availableCount)
            )
        }

        return ResponseEntity.ok(mapOf("status" to "accepted", "idempotencyKey" to key))
    }

    @GetMapping("/received")
    fun getReceivedAri(): List<OtaInventory> = otaInventoryRepository.findAll()

    @GetMapping("/inventory")
    fun getInventory(
        @RequestParam roomTypeId: String,
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): List<OtaInventory> {
        return otaInventoryRepository.findByRoomTypeIdAndDateRange(roomTypeId, startDate, endDate)
    }

    @PostMapping("/clear")
    fun clearReceived() {
        otaInventoryRepository.deleteAll()
        processedIdempotencyKeys.clear()
    }
}
