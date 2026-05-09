package com.stayops.channel.application.dto

import com.stayops.channel.domain.model.ChannelStatus

data class SyncDashboardResult(
    val channels: List<ChannelSyncStatus>,
    val summary: SyncSummary
)

data class ChannelSyncStatus(
    val channelCode: String,
    val channelName: String,
    val status: ChannelStatus,
    val pendingCount: Long,
    val completedCount: Long,
    val skippedCount: Long,
    val failedCount: Long
)

data class SyncSummary(
    val totalPending: Long,
    val totalCompleted: Long,
    val totalSkipped: Long,
    val totalFailed: Long
)
