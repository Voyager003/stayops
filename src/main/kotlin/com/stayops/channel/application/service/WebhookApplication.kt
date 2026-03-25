package com.stayops.channel.application.service

import com.stayops.channel.domain.model.MappingType
import com.stayops.channel.domain.model.ProcessedWebhookEvent
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.ProcessedWebhookEventRepository
import com.stayops.channel.domain.service.SignatureVerifier
import com.stayops.shared.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class WebhookApplication(
    private val channelRepository: ChannelRepository,
    private val channelMappingRepository: ChannelMappingRepository,
    private val processedEventRepository: ProcessedWebhookEventRepository,
    private val signatureVerifier: SignatureVerifier,
    private val channelSyncApplication: ChannelSyncApplication
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handleWebhook(
        propertyId: String,
        channelCode: String,
        signature: String,
        rawBody: String,
        eventId: String,
        eventType: String,
        payload: Map<String, Any>
    ) {
        val channel = channelRepository.findByPropertyIdAndCode(propertyId, channelCode)
            ?: throw BusinessException(code = "CHANNEL_NOT_FOUND", message = "채널을 찾을 수 없습니다: $channelCode")

        val webhookSecret = channel.connectionInfo?.webhookSecret
            ?: throw BusinessException(code = "WEBHOOK_SECRET_MISSING", message = "webhookSecret이 설정되지 않았습니다: $channelCode")

        if (!signatureVerifier.verify(webhookSecret, rawBody, signature)) {
            throw BusinessException(code = "INVALID_SIGNATURE", message = "Webhook 서명이 유효하지 않습니다")
        }

        try {
            processedEventRepository.save(
                ProcessedWebhookEvent(
                    id = UUID.randomUUID().toString(),
                    eventId = eventId,
                    channelCode = channelCode,
                    propertyId = propertyId
                )
            )
        } catch (e: DuplicateKeyException) {
            log.info("중복 이벤트 (동시 요청): eventId={}", eventId)
            return
        }

        val mapping = channelMappingRepository.findByPropertyIdAndChannelCode(propertyId, channelCode)

        when (eventType) {
            "BOOKING" -> {
                val roomTypeCode = payload["roomTypeCode"]?.toString()
                val internalRoomTypeId = roomTypeCode?.let {
                    mapping?.findInternalId(it, MappingType.ROOM_TYPE)
                }
                log.info(
                    "OTA 예약 수신: channelCode={}, roomTypeCode={}, internalRoomTypeId={}, bookingId={}",
                    channelCode, roomTypeCode, internalRoomTypeId, payload["bookingId"]
                )
                // Phase 8에서 ReservationService에 위임
            }
            "CANCELLATION" -> {
                log.info("OTA 취소 수신: channelCode={}, bookingId={}", channelCode, payload["bookingId"])
                // Phase 8에서 ReservationService에 위임
            }
            else -> {
                log.warn("알 수 없는 이벤트 타입: {}", eventType)
            }
        }

        log.info("Webhook 처리 완료: eventId={}", eventId)
    }
}
