package com.stayops.channel.application.service

import com.stayops.channel.application.dto.InventoryCompareItem
import com.stayops.channel.application.dto.InventoryCompareResult
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.application.required.ChannelInventorySnapshotReader
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class ChannelInventoryComparisonApplication(
    private val channelRepository: ChannelRepository,
    private val roomInventoryRepository: RoomInventoryRepository,
    private val inventorySnapshotReader: ChannelInventorySnapshotReader
) {
    fun compareInventory(
        propertyId: String,
        channelId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): InventoryCompareResult {
        val channel = channelRepository.findById(channelId)
            ?: throw NotFoundException(code = "CHANNEL_NOT_FOUND", message = "채널을 찾을 수 없습니다: $channelId")
        if (channel.propertyId != propertyId) {
            throw NotFoundException(code = "CHANNEL_NOT_FOUND", message = "채널을 찾을 수 없습니다: $channelId")
        }
        val apiEndpoint = channel.connectionInfo?.apiEndpoint
            ?: throw BusinessException(
                code = "DIRECT_CHANNEL_NOT_SUPPORTED",
                message = "OTA 채널만 재고를 조회할 수 있습니다"
            )

        val otaSnapshots = inventorySnapshotReader.fetchInventory(
            apiEndpoint,
            propertyId,
            channel.code,
            roomTypeId,
            startDate,
            endDate
        )
        val otaByDate = otaSnapshots.associateBy { it.date.toString() }

        val pmsInventories = roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
            propertyId, roomTypeId, startDate, endDate
        )
        val pmsByDate = pmsInventories.associateBy { it.date.toString() }

        val items = (otaByDate.keys + pmsByDate.keys).sorted().map { date ->
            val pmsAvailable = pmsByDate[date]?.availableCount ?: 0
            val otaAvailable = otaByDate[date]?.availableCount ?: 0
            InventoryCompareItem(
                date = date,
                pmsAvailableCount = pmsAvailable,
                otaAvailableCount = otaAvailable,
                isSynced = pmsAvailable == otaAvailable
            )
        }

        return InventoryCompareResult(
            channelCode = channel.code,
            channelName = channel.name,
            items = items
        )
    }
}
