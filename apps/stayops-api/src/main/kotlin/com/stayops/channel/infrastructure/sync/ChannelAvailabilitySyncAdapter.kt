package com.stayops.channel.infrastructure.sync

import com.stayops.channel.application.service.ChannelSyncApplication
import com.stayops.inventory.application.port.AvailabilitySyncPort
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ChannelAvailabilitySyncAdapter(
    private val channelSyncApplication: ChannelSyncApplication
) : AvailabilitySyncPort {

    override fun requestAvailabilitySync(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        availableCount: Int
    ) {
        channelSyncApplication.createAvailabilitySyncTasks(propertyId, roomTypeId, date, availableCount)
    }
}
