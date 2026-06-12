package com.stayops.channel.api

import com.stayops.channel.api.dto.SyncDashboardResponse
import com.stayops.channel.api.dto.SyncTaskResponse
import com.stayops.channel.application.service.ChannelSyncApplication
import com.stayops.channel.application.service.SyncDashboardApplication
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.member.application.service.MemberAccessApplication
import com.stayops.member.domain.model.Member
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/properties/{propertyId}")
class SyncDashboardApi(
    private val syncDashboardApplication: SyncDashboardApplication,
    private val channelSyncApplication: ChannelSyncApplication,
    private val memberAccessApplication: MemberAccessApplication
) {

    @GetMapping("/sync-dashboard")
    fun getDashboard(
        @PathVariable propertyId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<SyncDashboardResponse> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val result = syncDashboardApplication.getDashboard(propertyId)
        return ResponseEntity.ok(SyncDashboardResponse.from(result))
    }

    @GetMapping("/sync-tasks")
    fun getSyncTasks(
        @PathVariable propertyId: String,
        @RequestParam(required = false) status: SyncTaskStatus?,
        @RequestParam(required = false) channelCode: String?,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<List<SyncTaskResponse>> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val tasks = syncDashboardApplication.getSyncTasks(propertyId, status, channelCode)
        return ResponseEntity.ok(tasks.map { SyncTaskResponse.from(it) })
    }

    @PostMapping("/sync-tasks/{taskId}/retry")
    fun retryTask(
        @PathVariable propertyId: String,
        @PathVariable taskId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<Map<String, String>> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        channelSyncApplication.retryTask(propertyId, taskId)
        return ResponseEntity.ok(mapOf("status" to "retried", "taskId" to taskId))
    }

    @PostMapping("/sync-tasks/retry-all-failed")
    fun retryAllFailed(
        @PathVariable propertyId: String,
        @AuthenticationPrincipal member: Member?
    ): ResponseEntity<Map<String, Any>> {
        memberAccessApplication.requirePropertyAccess(member, propertyId)
        val failedTasks = syncDashboardApplication.getSyncTasks(propertyId, SyncTaskStatus.FAILED, null)
        failedTasks.forEach { channelSyncApplication.retryTask(propertyId, it.id) }
        return ResponseEntity.ok(mapOf("status" to "retried", "count" to failedTasks.size))
    }
}
