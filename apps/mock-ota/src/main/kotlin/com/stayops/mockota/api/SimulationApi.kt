package com.stayops.mockota.api

import com.stayops.mockota.model.MockBooking
import com.stayops.mockota.dao.OtaInventoryDao
import com.stayops.mockota.service.FailureMode
import com.stayops.mockota.service.FailureSimulatorService
import com.stayops.mockota.service.WebhookSenderService
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/simulate")
class SimulationApi(
    private val webhookSender: WebhookSenderService,
    private val failureSimulator: FailureSimulatorService,
    private val otaInventoryDao: OtaInventoryDao,
    private val mongoTemplate: MongoTemplate
) {

    private val log = LoggerFactory.getLogger(javaClass)

    data class BookingSimulationRequest(
        val propertyId: String,
        val channelCode: String,
        val webhookSecret: String,
        val roomTypeCode: String,
        val checkInDate: LocalDate,
        val checkOutDate: LocalDate,
        val guestName: String
    )

    data class CancellationSimulationRequest(
        val propertyId: String,
        val channelCode: String,
        val webhookSecret: String,
        val bookingId: String
    )

    data class RandomBookingRequest(
        val propertyId: String,
        val channelCode: String
    )

    data class InventoryBookingRequest(
        val propertyId: String,
        val channelCode: String,
        val roomTypeCode: String,
        val date: LocalDate
    )

    data class FailureModeRequest(
        val type: String,
        val delayMs: Long = 0
    )

    companion object {
        private val GUEST_NAMES = listOf(
            "김민수", "이지은", "박서준", "최유나",
            "정현우", "한소희", "강동원", "송혜교"
        )
    }

    @PostMapping("/random-booking")
    fun simulateRandomBooking(@RequestBody request: RandomBookingRequest): ResponseEntity<Map<String, String>> {
        log.info(
            "랜덤 예약 시뮬레이션 요청 수신: propertyId={}, channelCode={}",
            request.propertyId,
            request.channelCode
        )

        val availableInventory = otaInventoryDao.findByPropertyIdAndChannelCodeAndAvailableCountGreaterThan(
            request.propertyId,
            request.channelCode,
            0
        )
        if (availableInventory.isEmpty()) {
            log.warn(
                "랜덤 예약 시뮬레이션 실패: 예약 가능한 재고 없음 propertyId={}, channelCode={}",
                request.propertyId,
                request.channelCode
            )
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "예약 가능한 재고가 없습니다"))
        }

        val selected = availableInventory.random()
        val reserved = reserveInventory(
            propertyId = selected.propertyId,
            channelCode = selected.channelCode,
            roomTypeCode = selected.roomTypeId,
            date = selected.date
        ) ?: return ResponseEntity.badRequest()
            .body(mapOf("error" to "예약 가능한 재고가 없습니다"))
        val guestName = GUEST_NAMES.random()
        val checkInDate = LocalDate.parse(reserved.date)

        val booking = MockBooking(
            roomTypeCode = reserved.roomTypeId,
            checkInDate = checkInDate,
            checkOutDate = checkInDate.plusDays(1),
            guestName = guestName
        )

        log.info(
            "랜덤 예약 시뮬레이션 성공: propertyId={}, channelCode={}, roomTypeId={}, date={}, bookingId={}",
            request.propertyId,
            request.channelCode,
            reserved.roomTypeId,
            reserved.date,
            booking.bookingId
        )

        webhookSender.sendBookingWebhook(
            propertyId = request.propertyId,
            channelCode = request.channelCode,
            webhookSecret = request.channelCode,
            booking = booking
        )

        return ResponseEntity.ok(
            mapOf(
                "status" to "sent",
                "bookingId" to booking.bookingId,
                "roomTypeId" to reserved.roomTypeId,
                "date" to reserved.date,
                "guestName" to guestName
            )
        )
    }

    @PostMapping("/inventory-booking")
    fun simulateInventoryBooking(@RequestBody request: InventoryBookingRequest): ResponseEntity<Map<String, String>> {
        val reserved = reserveInventory(
            propertyId = request.propertyId,
            channelCode = request.channelCode,
            roomTypeCode = request.roomTypeCode,
            date = request.date.toString()
        ) ?: return ResponseEntity.badRequest()
            .body(mapOf("error" to "예약 가능한 재고가 없습니다"))

        val guestName = GUEST_NAMES.random()
        val booking = MockBooking(
            roomTypeCode = reserved.roomTypeId,
            checkInDate = request.date,
            checkOutDate = request.date.plusDays(1),
            guestName = guestName
        )

        webhookSender.sendBookingWebhook(
            propertyId = request.propertyId,
            channelCode = request.channelCode,
            webhookSecret = request.channelCode,
            booking = booking
        )

        return ResponseEntity.ok(
            mapOf(
                "status" to "sent",
                "bookingId" to booking.bookingId,
                "roomTypeId" to reserved.roomTypeId,
                "date" to reserved.date,
                "guestName" to guestName
            )
        )
    }

    @PostMapping("/booking")
    fun simulateBooking(@RequestBody request: BookingSimulationRequest): ResponseEntity<Map<String, String>> {
        val booking = MockBooking(
            roomTypeCode = request.roomTypeCode,
            checkInDate = request.checkInDate,
            checkOutDate = request.checkOutDate,
            guestName = request.guestName
        )
        webhookSender.sendBookingWebhook(
            propertyId = request.propertyId,
            channelCode = request.channelCode,
            webhookSecret = request.webhookSecret,
            booking = booking
        )
        return ResponseEntity.ok(mapOf("status" to "sent", "bookingId" to booking.bookingId))
    }

    @PostMapping("/cancellation")
    fun simulateCancellation(@RequestBody request: CancellationSimulationRequest): ResponseEntity<Map<String, String>> {
        webhookSender.sendCancellationWebhook(
            propertyId = request.propertyId,
            channelCode = request.channelCode,
            webhookSecret = request.webhookSecret,
            bookingId = request.bookingId
        )
        return ResponseEntity.ok(mapOf("status" to "sent"))
    }

    @PostMapping("/failure-mode")
    fun setFailureMode(@RequestBody request: FailureModeRequest): ResponseEntity<Map<String, String>> {
        failureSimulator.setFailureMode(FailureMode(type = request.type, delayMs = request.delayMs))
        return ResponseEntity.ok(mapOf("status" to "enabled", "type" to request.type))
    }

    @PostMapping("/failure-mode/clear")
    fun clearFailureMode(): ResponseEntity<Map<String, String>> {
        failureSimulator.clearAll()
        return ResponseEntity.ok(mapOf("status" to "cleared"))
    }

    private fun reserveInventory(
        propertyId: String,
        channelCode: String,
        roomTypeCode: String,
        date: String
    ): com.stayops.mockota.model.OtaInventory? {
        val query = Query.query(
            Criteria.where("propertyId").`is`(propertyId)
                .and("channelCode").`is`(channelCode)
                .and("roomTypeId").`is`(roomTypeCode)
                .and("date").`is`(date)
                .and("availableCount").gt(0)
        )
        val update = Update()
            .inc("availableCount", -1)
            .set("updatedAt", java.time.Instant.now())
        return mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            com.stayops.mockota.model.OtaInventory::class.java
        )
    }
}
