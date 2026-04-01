package com.stayops.channel.api

import com.stayops.channel.application.service.ChannelApplication
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.security.PropertyAccessChecker
import org.springframework.core.ParameterizedTypeReference
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/channels/{channelId}/inventory")
class ChannelInventoryApi(
    private val propertyAccessChecker: PropertyAccessChecker,
    private val channelApplication: ChannelApplication,
    private val roomInventoryApplication: RoomInventoryApplication
) {

    private val restClient = RestClient.create()

    @GetMapping
    fun compareInventory(
        @PathVariable propertyId: String,
        @PathVariable channelId: String,
        @RequestParam roomTypeId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate
    ): ResponseEntity<ChannelInventoryCompareResponse> {
        propertyAccessChecker.requireAccess(propertyId)

        val channel = channelApplication.findChannel(propertyId, channelId)

        val apiEndpoint = channel.connectionInfo?.apiEndpoint
            ?: throw BusinessException(
                code = "DIRECT_CHANNEL_NOT_SUPPORTED",
                message = "OTA 채널만 재고를 조회할 수 있습니다"
            )

        val otaItems = try {
            restClient.get()
                .uri(
                    "$apiEndpoint/api/v1/ari/inventory?roomTypeId={roomTypeId}&startDate={startDate}&endDate={endDate}",
                    roomTypeId, startDate.toString(), endDate.toString()
                )
                .retrieve()
                .body(object : ParameterizedTypeReference<List<OtaInventoryItem>>() {})
                ?: emptyList()
        } catch (e: Exception) {
            throw BusinessException(
                code = "OTA_CONNECTION_FAILED",
                message = "OTA 서버에 연결할 수 없습니다: ${e.message}"
            )
        }

        val otaByDate = otaItems.associateBy { it.date }

        val pmsInventories = roomInventoryApplication.getAvailability(propertyId, roomTypeId, startDate, endDate)
        val pmsByDate = pmsInventories.associateBy { it.date.toString() }

        val allDates = (otaByDate.keys + pmsByDate.keys).sorted()

        val items = allDates.map { date ->
            val pmsAvailable = pmsByDate[date]?.availableCount ?: 0
            val otaAvailable = otaByDate[date]?.availableCount ?: 0
            InventoryCompareItem(
                date = date,
                pmsAvailableCount = pmsAvailable,
                otaAvailableCount = otaAvailable,
                isSynced = pmsAvailable == otaAvailable
            )
        }

        return ResponseEntity.ok(
            ChannelInventoryCompareResponse(
                channelCode = channel.code,
                channelName = channel.name,
                items = items
            )
        )
    }
}

data class ChannelInventoryCompareResponse(
    val channelCode: String,
    val channelName: String,
    val items: List<InventoryCompareItem>
)

data class InventoryCompareItem(
    val date: String,
    val pmsAvailableCount: Int,
    val otaAvailableCount: Int,
    val isSynced: Boolean
)

data class OtaInventoryItem(
    val roomTypeId: String,
    val date: String,
    val availableCount: Int
)
