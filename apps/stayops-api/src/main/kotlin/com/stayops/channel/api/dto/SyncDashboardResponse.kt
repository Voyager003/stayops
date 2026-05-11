package com.stayops.channel.api.dto

import com.stayops.channel.application.dto.SyncDashboardResult
import com.stayops.channel.domain.model.ChannelStatus

data class SyncDashboardResponse(
    val channels: List<ChannelSyncStatusResponse>,
    val summary: SyncSummaryResponse
) {
    companion object {
        fun from(result: SyncDashboardResult) = SyncDashboardResponse(
            channels = result.channels.map {
                ChannelSyncStatusResponse(
                    channelCode = it.channelCode,
                    channelName = it.channelName,
                    status = it.status,
                    pendingCount = it.pendingCount,
                    completedCount = it.completedCount,
                    skippedCount = it.skippedCount,
                    failedCount = it.failedCount
                )
            },
            summary = SyncSummaryResponse(
                totalPending = result.summary.totalPending,
                totalCompleted = result.summary.totalCompleted,
                totalSkipped = result.summary.totalSkipped,
                totalFailed = result.summary.totalFailed
            )
        )
    }
}

data class ChannelSyncStatusResponse(
    val channelCode: String,
    val channelName: String,
    val status: ChannelStatus,
    val pendingCount: Long,
    val completedCount: Long,
    val skippedCount: Long,
    val failedCount: Long
)

data class SyncSummaryResponse(
    val totalPending: Long,
    val totalCompleted: Long,
    val totalSkipped: Long,
    val totalFailed: Long
)
