package com.stayops.channel.api

import com.stayops.channel.api.dto.SyncDashboardResponse
import com.stayops.channel.api.dto.SyncTaskResponse
import com.stayops.channel.application.service.ChannelSyncApplication
import com.stayops.channel.application.service.SyncDashboardApplication
import com.stayops.member.infrastructure.security.PropertyAccessChecker
import com.stayops.channel.domain.model.SyncTaskStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/properties/{propertyId}")
class SyncDashboardApi(
    private val syncDashboardApplication: SyncDashboardApplication,
    private val channelSyncApplication: ChannelSyncApplication,
    private val propertyAccessChecker: PropertyAccessChecker
) {

    @GetMapping("/sync-dashboard")
    fun getDashboard(@PathVariable propertyId: String): ResponseEntity<SyncDashboardResponse> {
        propertyAccessChecker.requireAccess(propertyId)
        val result = syncDashboardApplication.getDashboard(propertyId)
        return ResponseEntity.ok(SyncDashboardResponse.from(result))
    }

    @GetMapping("/sync-tasks")
    fun getSyncTasks(
        @PathVariable propertyId: String,
        @RequestParam(required = false) status: SyncTaskStatus?,
        @RequestParam(required = false) channelCode: String?
    ): ResponseEntity<List<SyncTaskResponse>> {
        propertyAccessChecker.requireAccess(propertyId)
        val tasks = syncDashboardApplication.getSyncTasks(propertyId, status, channelCode)
        return ResponseEntity.ok(tasks.map { SyncTaskResponse.from(it) })
    }

    @PostMapping("/sync-tasks/{taskId}/retry")
    fun retryTask(
        @PathVariable propertyId: String,
        @PathVariable taskId: String
    ): ResponseEntity<Map<String, String>> {
        propertyAccessChecker.requireAccess(propertyId)
        channelSyncApplication.retryTask(propertyId, taskId)
        return ResponseEntity.ok(mapOf("status" to "retried", "taskId" to taskId))
    }

    @PostMapping("/sync-tasks/retry-all-failed")
    fun retryAllFailed(@PathVariable propertyId: String): ResponseEntity<Map<String, Any>> {
        propertyAccessChecker.requireAccess(propertyId)
        val failedTasks = syncDashboardApplication.getSyncTasks(propertyId, SyncTaskStatus.FAILED, null)
        failedTasks.forEach { channelSyncApplication.retryTask(propertyId, it.id) }
        return ResponseEntity.ok(mapOf("status" to "retried", "count" to failedTasks.size))
    }
}
