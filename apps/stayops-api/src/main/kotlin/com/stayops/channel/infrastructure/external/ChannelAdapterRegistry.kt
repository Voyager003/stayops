package com.stayops.channel.infrastructure.external

import com.stayops.channel.domain.service.ChannelAdapterProvider
import com.stayops.channel.domain.service.ChannelSyncAdapter
import org.springframework.stereotype.Component

@Component
class ChannelAdapterRegistry(
    private val defaultAdapter: HttpChannelSyncAdapter
) : ChannelAdapterProvider {
    private val adapters = mutableMapOf<String, ChannelSyncAdapter>()

    override fun getAdapter(channelCode: String): ChannelSyncAdapter =
        adapters.getOrDefault(channelCode, defaultAdapter)

    fun registerAdapter(channelCode: String, adapter: ChannelSyncAdapter) {
        adapters[channelCode] = adapter
    }
}
