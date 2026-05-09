package com.stayops.channel.domain.service

interface ChannelAdapterProvider {
    fun getAdapter(channelCode: String): ChannelSyncAdapter
}
