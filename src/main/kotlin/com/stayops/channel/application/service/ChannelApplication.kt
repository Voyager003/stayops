package com.stayops.channel.application.service

import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.exception.NotFoundException
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
    @Value("\${mock-ota.endpoint}") private val otaEndpoint: String
) {

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
        return channelRepository.save(updated)
    }

    fun activateChannel(propertyId: String, channelId: String): Channel {
        val channel = findChannel(propertyId, channelId)
        return channelRepository.save(channel.activate())
    }

    fun deactivateChannel(propertyId: String, channelId: String): Channel {
        val channel = findChannel(propertyId, channelId)
        return channelRepository.save(channel.deactivate())
    }

    fun suspendChannel(propertyId: String, channelId: String): Channel {
        val channel = findChannel(propertyId, channelId)
        return channelRepository.save(channel.suspend())
    }

    fun deleteChannel(propertyId: String, channelId: String) {
        findChannel(propertyId, channelId)
        channelRepository.deleteById(channelId)
    }

}
