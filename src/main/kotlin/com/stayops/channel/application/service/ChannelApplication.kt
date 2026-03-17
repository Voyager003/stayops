package com.stayops.channel.application.service

import com.stayops.channel.api.dto.CreateChannelRequest
import com.stayops.channel.api.dto.UpdateChannelRequest
import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ChannelApplication(
    private val channelRepository: ChannelRepository,
    private val channelMappingRepository: ChannelMappingRepository
) {

    fun createOtaChannel(propertyId: String, request: CreateChannelRequest): Channel {
        val channel = Channel.createOta(
            id = UUID.randomUUID().toString(),
            propertyId = propertyId,
            code = request.code,
            name = request.name,
            commissionRate = request.commissionRate,
            connectionInfo = ChannelConnectionInfo(
                apiEndpoint = request.apiEndpoint,
                apiKey = request.apiKey,
                apiSecret = request.apiSecret,
                webhookSecret = request.webhookSecret
            )
        )
        return channelRepository.save(channel)
    }

    fun findChannels(propertyId: String): List<Channel> =
        channelRepository.findByPropertyId(propertyId)

    fun findChannel(propertyId: String, channelId: String): Channel =
        channelRepository.findById(channelId)
            ?: throw NotFoundException(code = "CHANNEL_NOT_FOUND", message = "채널을 찾을 수 없습니다: $channelId")

    fun updateChannel(propertyId: String, channelId: String, request: UpdateChannelRequest): Channel {
        val channel = findChannel(propertyId, channelId)
        val updated = Channel.reconstitute(
            id = channel.id,
            propertyId = channel.propertyId,
            code = channel.code,
            name = request.name ?: channel.name,
            type = channel.type,
            commissionRate = request.commissionRate ?: channel.commissionRate,
            connectionInfo = if (channel.connectionInfo != null) {
                ChannelConnectionInfo(
                    apiEndpoint = request.apiEndpoint ?: channel.connectionInfo!!.apiEndpoint,
                    apiKey = request.apiKey ?: channel.connectionInfo!!.apiKey,
                    apiSecret = request.apiSecret ?: channel.connectionInfo!!.apiSecret,
                    webhookSecret = request.webhookSecret ?: channel.connectionInfo!!.webhookSecret
                )
            } else channel.connectionInfo,
            status = channel.status,
            version = channel.version,
            createdAt = channel.createdAt,
            updatedAt = java.time.Instant.now()
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

    // -- Mapping --

    fun getMappings(propertyId: String, channelCode: String): ChannelMapping? =
        channelMappingRepository.findByPropertyIdAndChannelCode(propertyId, channelCode)

    fun addMapping(propertyId: String, channelCode: String, entry: MappingEntry): ChannelMapping {
        val mapping = channelMappingRepository.findByPropertyIdAndChannelCode(propertyId, channelCode)
            ?: ChannelMapping.create(
                id = UUID.randomUUID().toString(),
                propertyId = propertyId,
                channelCode = channelCode
            )
        val updated = mapping.addMapping(entry)
        return channelMappingRepository.save(updated)
    }

    fun removeMapping(propertyId: String, channelCode: String, internalId: String, type: MappingType): ChannelMapping {
        val mapping = channelMappingRepository.findByPropertyIdAndChannelCode(propertyId, channelCode)
            ?: throw NotFoundException(code = "MAPPING_NOT_FOUND", message = "매핑을 찾을 수 없습니다: $channelCode")
        val updated = mapping.removeMapping(internalId, type)
        return channelMappingRepository.save(updated)
    }
}
