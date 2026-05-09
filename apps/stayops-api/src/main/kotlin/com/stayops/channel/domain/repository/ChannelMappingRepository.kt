package com.stayops.channel.domain.repository

import com.stayops.channel.domain.model.ChannelMapping

interface ChannelMappingRepository {
    fun save(mapping: ChannelMapping): ChannelMapping
    fun findByPropertyIdAndChannelCode(propertyId: String, channelCode: String): ChannelMapping?
    fun deleteByPropertyIdAndChannelCode(propertyId: String, channelCode: String)
}
