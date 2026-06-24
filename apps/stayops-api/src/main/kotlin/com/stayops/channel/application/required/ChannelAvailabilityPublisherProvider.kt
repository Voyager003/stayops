package com.stayops.channel.application.required

interface ChannelAvailabilityPublisherProvider {
    fun getPublisher(channelCode: String): ChannelAvailabilityPublisher
}
