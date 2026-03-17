package com.stayops.channel.infrastructure.scheduler

import com.stayops.channel.application.service.ChannelSyncApplication
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SyncTaskScheduler(
    private val channelSyncApplication: ChannelSyncApplication
) {

    @Scheduled(fixedDelay = 30_000)
    fun pollAndProcess() {
        channelSyncApplication.processPendingTasks()
    }
}
