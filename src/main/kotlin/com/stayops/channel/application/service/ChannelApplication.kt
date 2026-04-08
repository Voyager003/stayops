package com.stayops.channel.application.service

import com.stayops.channel.application.dto.InventoryCompareItem
import com.stayops.channel.application.dto.InventoryCompareResult
import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.service.ChannelInventoryQueryAdapter
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class ChannelApplication(
    private val channelRepository: ChannelRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val roomInventoryRepository: RoomInventoryRepository,
    private val channelSyncApplication: ChannelSyncApplication,
    private val inventoryQueryAdapter: ChannelInventoryQueryAdapter,
    @Value("\${mock-ota.endpoint}") private val otaEndpoint: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun createOtaChannel(
        propertyId: String,
        code: String,
        name: String,
        commissionRate: BigDecimal
    ): Channel {
        val channel = Channel.createOta(
            id = UUID.randomUUID().toString(),
            propertyId = propertyId,
            code = code,
            name = name,
            commissionRate = commissionRate,
            apiEndpoint = otaEndpoint
        )
        val saved = channelRepository.save(channel)

        // Initial inventory sync for the new channel
        val roomTypes = roomTypeRepository.findByPropertyId(propertyId)
        val today = LocalDate.now()
        val endDate = today.plusDays(90)
        for (roomType in roomTypes) {
            val inventories = roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                propertyId, roomType.id, today, endDate
            )
            for (inventory in inventories) {
                channelSyncApplication.createAvailabilitySyncTasks(
                    propertyId, roomType.id, inventory.date, inventory.availableCount
                )
            }
        }

        log.info("OTA 채널 생성: channelId={}, propertyId={}, code={}", saved.id, propertyId, code)
        return saved
    }

    fun findChannels(propertyId: String): List<Channel> =
        channelRepository.findByPropertyId(propertyId)

    fun findChannel(propertyId: String, channelId: String): Channel {
        val channel = channelRepository.findById(channelId)
            ?: throw NotFoundException(code = "CHANNEL_NOT_FOUND", message = "채널을 찾을 수 없습니다: $channelId")
        if (channel.propertyId != propertyId) {
            throw NotFoundException(code = "CHANNEL_NOT_FOUND", message = "채널을 찾을 수 없습니다: $channelId")
        }
        return channel
    }

    fun updateChannel(
        propertyId: String,
        channelId: String,
        name: String? = null,
        commissionRate: BigDecimal? = null
    ): Channel {
        val channel = findChannel(propertyId, channelId)
        val updated = Channel.reconstitute(
            id = channel.id,
            propertyId = channel.propertyId,
            code = channel.code,
            name = name ?: channel.name,
            type = channel.type,
            commissionRate = commissionRate ?: channel.commissionRate,
            connectionInfo = channel.connectionInfo,
            status = channel.status,
            version = channel.version,
            createdAt = channel.createdAt,
            updatedAt = Instant.now()
        )
        val saved = channelRepository.save(updated)
        log.info("채널 수정: channelId={}, propertyId={}", channelId, propertyId)
        return saved
    }

    fun activateChannel(propertyId: String, channelId: String): Channel {
        val channel = findChannel(propertyId, channelId)
        log.info("채널 활성화: channelId={}, propertyId={}", channelId, propertyId)
        return channelRepository.save(channel.activate())
    }

    fun deactivateChannel(propertyId: String, channelId: String): Channel {
        val channel = findChannel(propertyId, channelId)
        log.info("채널 비활성화: channelId={}, propertyId={}", channelId, propertyId)
        return channelRepository.save(channel.deactivate())
    }

    fun suspendChannel(propertyId: String, channelId: String): Channel {
        val channel = findChannel(propertyId, channelId)
        log.info("채널 정지: channelId={}, propertyId={}", channelId, propertyId)
        return channelRepository.save(channel.suspend())
    }

    fun deleteChannel(propertyId: String, channelId: String) {
        findChannel(propertyId, channelId)
        channelRepository.deleteById(channelId)
        log.info("채널 삭제: channelId={}, propertyId={}", channelId, propertyId)
    }

    fun compareInventory(
        propertyId: String,
        channelId: String,
        roomTypeId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): InventoryCompareResult {
        val channel = findChannel(propertyId, channelId)
        val apiEndpoint = channel.connectionInfo?.apiEndpoint
            ?: throw BusinessException(
                code = "DIRECT_CHANNEL_NOT_SUPPORTED",
                message = "OTA 채널만 재고를 조회할 수 있습니다"
            )

        val otaSnapshots = inventoryQueryAdapter.fetchInventory(apiEndpoint, roomTypeId, startDate, endDate)
        val otaByDate = otaSnapshots.associateBy { it.date.toString() }

        val pmsInventories = roomInventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
            propertyId, roomTypeId, startDate, endDate
        )
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

        return InventoryCompareResult(
            channelCode = channel.code,
            channelName = channel.name,
            items = items
        )
    }

}
