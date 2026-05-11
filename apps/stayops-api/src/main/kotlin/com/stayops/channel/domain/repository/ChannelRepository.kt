package com.stayops.channel.domain.repository

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.model.ChannelStatus

interface ChannelRepository {
    fun save(channel: Channel): Channel
    fun findById(id: String): Channel?
    fun findByPropertyId(propertyId: String): List<Channel>
    fun findByPropertyIdAndCode(propertyId: String, code: String): Channel?
    fun findByPropertyIdAndStatus(propertyId: String, status: ChannelStatus): List<Channel>
    fun deleteById(id: String)
}
