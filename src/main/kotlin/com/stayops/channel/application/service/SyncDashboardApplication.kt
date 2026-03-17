package com.stayops.channel.application.service

import com.stayops.channel.api.dto.ChannelSyncStatusResponse
import com.stayops.channel.api.dto.SyncDashboardResponse
import com.stayops.channel.api.dto.SyncSummary
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.model.ChannelType
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.SyncTaskRepository
import org.springframework.stereotype.Service

@Service
class SyncDashboardApplication(
    private val channelRepository: ChannelRepository,
    private val syncTaskRepository: SyncTaskRepository
) {

    fun getDashboard(propertyId: String): SyncDashboardResponse {
        val channels = channelRepository.findByPropertyId(propertyId)
            .filter { it.type == ChannelType.OTA }

        val channelStatuses = channels.map { channel ->
            val counts = syncTaskRepository.countByPropertyIdAndChannelCodeGroupByStatus(
                propertyId, channel.code
            )
            ChannelSyncStatusResponse(
                channelCode = channel.code,
                channelName = channel.name,
                status = channel.status,
                pendingCount = counts[SyncTaskStatus.PENDING] ?: 0L,
                completedCount = counts[SyncTaskStatus.COMPLETED] ?: 0L,
                failedCount = counts[SyncTaskStatus.FAILED] ?: 0L
            )
        }

        val summary = SyncSummary(
            totalPending = channelStatuses.sumOf { it.pendingCount },
            totalCompleted = channelStatuses.sumOf { it.completedCount },
            totalFailed = channelStatuses.sumOf { it.failedCount }
        )

        return SyncDashboardResponse(channels = channelStatuses, summary = summary)
    }

    fun getSyncTasks(
        propertyId: String,
        status: SyncTaskStatus?,
        channelCode: String?
    ): List<com.stayops.channel.domain.model.SyncTask> {
        return when {
            status != null && channelCode != null ->
                syncTaskRepository.findByPropertyIdAndChannelCodeAndStatus(propertyId, channelCode, status)
            status != null ->
                syncTaskRepository.findByPropertyIdAndStatus(propertyId, status)
            else ->
                syncTaskRepository.findByPropertyIdAndStatus(propertyId, SyncTaskStatus.PENDING) +
                syncTaskRepository.findByPropertyIdAndStatus(propertyId, SyncTaskStatus.FAILED)
        }
    }
}
