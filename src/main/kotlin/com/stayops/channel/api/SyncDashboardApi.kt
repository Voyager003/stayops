package com.stayops.channel.api

import com.stayops.channel.api.dto.SyncDashboardResponse
import com.stayops.channel.api.dto.SyncTaskResponse
import com.stayops.channel.application.service.ChannelSyncApplication
import com.stayops.channel.application.service.SyncDashboardApplication
import com.stayops.channel.domain.model.SyncTaskStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/properties/{propertyId}")
class SyncDashboardApi(
    private val syncDashboardApplication: SyncDashboardApplication,
    private val channelSyncApplication: ChannelSyncApplication
) {

    @GetMapping("/sync-dashboard")
    fun getDashboard(@PathVariable propertyId: String): ResponseEntity<SyncDashboardResponse> {
        val result = syncDashboardApplication.getDashboard(propertyId)
        return ResponseEntity.ok(SyncDashboardResponse.from(result))
    }

    @GetMapping("/sync-tasks")
    fun getSyncTasks(
        @PathVariable propertyId: String,
        @RequestParam(required = false) status: SyncTaskStatus?,
        @RequestParam(required = false) channelCode: String?
    ): ResponseEntity<List<SyncTaskResponse>> {
        val tasks = syncDashboardApplication.getSyncTasks(propertyId, status, channelCode)
        return ResponseEntity.ok(tasks.map { SyncTaskResponse.from(it) })
    }

    @PostMapping("/sync-tasks/{taskId}/retry")
    fun retryTask(
        @PathVariable propertyId: String,
        @PathVariable taskId: String
    ): ResponseEntity<Map<String, String>> {
        channelSyncApplication.retryTask(taskId)
        return ResponseEntity.ok(mapOf("status" to "retried", "taskId" to taskId))
    }

    @PostMapping("/sync-tasks/retry-all-failed")
    fun retryAllFailed(@PathVariable propertyId: String): ResponseEntity<Map<String, Any>> {
        val failedTasks = syncDashboardApplication.getSyncTasks(propertyId, SyncTaskStatus.FAILED, null)
        failedTasks.forEach { channelSyncApplication.retryTask(it.id) }
        return ResponseEntity.ok(mapOf("status" to "retried", "count" to failedTasks.size))
    }
}
