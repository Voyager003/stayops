package com.stayops.channel.application.service

import com.stayops.channel.application.dto.AvailabilityPayload
import com.stayops.channel.domain.model.ChannelStatus
import com.stayops.channel.domain.model.ChannelType
import com.stayops.channel.domain.model.SyncTask
import com.stayops.channel.domain.model.SyncTaskStatus
import com.stayops.channel.domain.model.SyncTaskType
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.SyncTaskRepository
import com.stayops.channel.domain.service.ChannelAdapterProvider
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class ChannelSyncApplication(
    private val channelRepository: ChannelRepository,
    private val syncTaskRepository: SyncTaskRepository,
    private val adapterProvider: ChannelAdapterProvider
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun createAvailabilitySyncTasks(
        propertyId: String,
        roomTypeId: String,
        date: LocalDate,
        availableCount: Int
    ) {
        val payload = AvailabilityPayload(
            roomTypeId = roomTypeId,
            date = date,
            availableCount = availableCount
        )

        val otaChannels = channelRepository.findByPropertyIdAndStatus(propertyId, ChannelStatus.ACTIVE)
            .filter { it.type == ChannelType.OTA }

        otaChannels.forEach { channel ->
            val task = SyncTask.create(
                id = UUID.randomUUID().toString(),
                propertyId = propertyId,
                channelCode = channel.code,
                type = SyncTaskType.AVAILABILITY_UPDATE,
                payload = payload.toMap()
            )
            syncTaskRepository.save(task)
            log.info("SyncTask 생성: propertyId={}, channelCode={}, roomTypeId={}", propertyId, channel.code, roomTypeId)
        }
    }

    fun processPendingTasks() {
        val tasks = syncTaskRepository.findPendingTasksReadyForProcessing(Instant.now())

        tasks.forEach { task ->
            try {
            val processing = task.startProcessing()
            syncTaskRepository.save(processing)

            try {
                val channel = channelRepository.findByPropertyIdAndCode(processing.propertyId, processing.channelCode)
                if (channel == null || channel.connectionInfo == null) {
                    syncTaskRepository.save(processing.skip("채널 또는 connectionInfo 없음"))
                    log.warn("채널 또는 connectionInfo 없음, 태스크 건너뜀: taskId={}", processing.id)
                    return@forEach
                }

                val roomTypeCode = processing.payload["roomTypeId"]?.toString() ?: ""

                val adapter = adapterProvider.getAdapter(processing.channelCode)
                val result = adapter.pushAvailability(
                    endpoint = channel.connectionInfo!!.apiEndpoint,
                    apiKey = null,
                    externalRoomTypeCode = roomTypeCode,
                    payload = processing.payload,
                    idempotencyKey = processing.idempotencyKey
                )

                if (result.success) {
                    syncTaskRepository.save(processing.complete())
                    log.info("ARI push 성공: taskId={}, channelCode={}", processing.id, processing.channelCode)
                } else {
                    syncTaskRepository.save(processing.fail(result.errorMessage ?: "Unknown error"))
                    log.warn("ARI push 실패: taskId={}, error={}", processing.id, result.errorMessage)
                }
            } catch (e: Exception) {
                syncTaskRepository.save(processing.fail(e.message ?: "Unexpected error"))
                log.error("ARI push 예외: taskId={}", processing.id, e)
            }
            } catch (e: ConflictException) {
                log.warn("SyncTask version 충돌, 다음 폴링에서 재시도: taskId={}", task.id)
            }
        }
    }

    @Async
    fun processTasksImmediately(propertyId: String) {
        val pendingTasks = syncTaskRepository.findByPropertyIdAndStatus(propertyId, SyncTaskStatus.PENDING)

        pendingTasks.forEach { task ->
            try {
                val channel = channelRepository.findByPropertyIdAndCode(task.propertyId, task.channelCode)
                if (channel == null || channel.connectionInfo == null) {
                    log.warn("즉시 전송 건너뜀 (채널/connectionInfo 없음): taskId={}", task.id)
                    return@forEach
                }

                val roomTypeCode = task.payload["roomTypeId"]?.toString() ?: ""
                val adapter = adapterProvider.getAdapter(task.channelCode)
                val result = adapter.pushAvailability(
                    endpoint = channel.connectionInfo!!.apiEndpoint,
                    apiKey = null,
                    externalRoomTypeCode = roomTypeCode,
                    payload = task.payload,
                    idempotencyKey = task.idempotencyKey
                )

                if (result.success) {
                    syncTaskRepository.save(task.startProcessing().complete())
                    log.info("즉시 전송 성공: taskId={}, channelCode={}", task.id, task.channelCode)
                }
                // 실패 시 PENDING 유지 → 폴링이 재시도
            } catch (e: ConflictException) {
                log.warn("즉시 전송 version 충돌 (이미 처리됨): taskId={}", task.id)
            } catch (e: Exception) {
                log.warn("즉시 전송 실패 (폴링이 재시도 예정): taskId={}, error={}", task.id, e.message)
            }
        }
    }

    fun retryTask(propertyId: String, taskId: String) {
        val task = syncTaskRepository.findById(taskId)
            ?: throw NotFoundException(code = "SYNC_TASK_NOT_FOUND", message = "SyncTask를 찾을 수 없습니다: $taskId")
        if (task.propertyId != propertyId) {
            throw NotFoundException(code = "SYNC_TASK_NOT_FOUND", message = "SyncTask를 찾을 수 없습니다: $taskId")
        }
        syncTaskRepository.save(task.retry())
    }
}
