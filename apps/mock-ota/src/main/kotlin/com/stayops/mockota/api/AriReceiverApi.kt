package com.stayops.mockota.api

import com.stayops.mockota.model.OtaInventory
import com.stayops.mockota.dao.OtaInventoryDao
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
    private val otaInventoryDao: OtaInventoryDao
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

        val propertyId = payload.requiredString("propertyId")
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "propertyId is required"))
        val channelCode = payload.requiredString("channelCode")
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "channelCode is required"))
        val roomTypeId = payload.requiredString("roomTypeCode")
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "roomTypeCode is required"))
        val date = payload.requiredString("date")
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "date is required"))
        val availableCount = (payload["availableCount"] as? Number)?.toInt()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "availableCount is required"))

        val existing = otaInventoryDao.findByPropertyIdAndChannelCodeAndRoomTypeIdAndDate(
            propertyId,
            channelCode,
            roomTypeId,
            date
        )
        if (existing != null) {
            otaInventoryDao.save(existing.copy(availableCount = availableCount, updatedAt = Instant.now()))
        } else {
            otaInventoryDao.save(
                OtaInventory(
                    propertyId = propertyId,
                    channelCode = channelCode,
                    roomTypeId = roomTypeId,
                    date = date,
                    availableCount = availableCount
                )
            )
        }

        return ResponseEntity.ok(mapOf("status" to "accepted", "idempotencyKey" to key))
    }

    @GetMapping("/received")
    fun getReceivedAri(): List<OtaInventory> = otaInventoryDao.findAll()

    @GetMapping("/inventory")
    fun getInventory(
        @RequestParam propertyId: String,
        @RequestParam channelCode: String,
        @RequestParam roomTypeId: String,
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): List<OtaInventory> {
        return otaInventoryDao.findByPropertyIdAndChannelCodeAndRoomTypeIdAndDateRange(
            propertyId,
            channelCode,
            roomTypeId,
            startDate,
            endDate
        )
    }

    @PostMapping("/clear")
    fun clearReceived() {
        otaInventoryDao.deleteAll()
        processedIdempotencyKeys.clear()
    }

    private fun Map<String, Any>.requiredString(key: String): String? =
        this[key]?.toString()?.takeIf { it.isNotBlank() }
}
