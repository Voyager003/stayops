package com.stayops.channel.api.dto

import com.stayops.channel.domain.model.ChannelStatus

data class SyncDashboardResponse(
    val channels: List<ChannelSyncStatusResponse>,
    val summary: SyncSummary
)

data class ChannelSyncStatusResponse(
    val channelCode: String,
    val channelName: String,
    val status: ChannelStatus,
    val pendingCount: Long,
    val completedCount: Long,
    val failedCount: Long
)

data class SyncSummary(
    val totalPending: Long,
    val totalCompleted: Long,
    val totalFailed: Long
)
