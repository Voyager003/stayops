package com.stayops.channel.infrastructure.external

import com.stayops.channel.application.required.ChannelAvailabilityPublisherProvider
import com.stayops.channel.application.required.ChannelAvailabilityPublisher
import org.springframework.stereotype.Component

@Component
class ChannelAvailabilityPublisherRegistry(
    private val defaultPublisher: HttpChannelAvailabilityPublisher
) : ChannelAvailabilityPublisherProvider {
    private val publishers = mutableMapOf<String, ChannelAvailabilityPublisher>()

    override fun getPublisher(channelCode: String): ChannelAvailabilityPublisher =
        publishers.getOrDefault(channelCode, defaultPublisher)

    fun registerPublisher(channelCode: String, publisher: ChannelAvailabilityPublisher) {
        publishers[channelCode] = publisher
    }
}
