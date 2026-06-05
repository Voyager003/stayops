package com.stayops.channel.domain.repository

import com.stayops.channel.domain.model.SyncTask
import com.stayops.channel.domain.model.SyncTaskStatus
import java.time.Instant

interface SyncTaskRepository {
    fun save(task: SyncTask): SyncTask
    fun findById(id: String): SyncTask?
    fun claimReadyForProcessing(workerId: String, now: Instant, lockedUntil: Instant): SyncTask?
    fun findPendingTasksReadyForProcessing(now: Instant): List<SyncTask>
    fun findByPropertyIdAndStatus(propertyId: String, status: SyncTaskStatus): List<SyncTask>
    fun findByPropertyIdAndChannelCodeAndStatus(
        propertyId: String,
        channelCode: String,
        status: SyncTaskStatus
    ): List<SyncTask>
    fun countByPropertyIdAndChannelCodeGroupByStatus(
        propertyId: String,
        channelCode: String
    ): Map<SyncTaskStatus, Long>
}
