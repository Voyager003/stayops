package com.stayops.channel.api

import com.stayops.channel.application.service.WebhookApplication
import com.stayops.shared.exception.BusinessException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/channels/webhook")
class ChannelWebhookApi(
    private val webhookApplication: WebhookApplication
) {

    @PostMapping("/{channelCode}")
    fun receiveWebhook(
        @PathVariable propertyId: String,
        @PathVariable channelCode: String,
        @RequestHeader("X-Webhook-Signature") signature: String,
        @RequestBody rawBody: String
    ): ResponseEntity<Map<String, String>> {
        val payload = parsePayload(rawBody)
        val eventId = payload["eventId"]?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(code = "INVALID_WEBHOOK", message = "eventId는 필수입니다")
        val eventType = payload["eventType"]?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(code = "INVALID_WEBHOOK", message = "eventType은 필수입니다")

        webhookApplication.handleWebhook(
            propertyId = propertyId,
            channelCode = channelCode,
            signature = signature,
            rawBody = rawBody,
            eventId = eventId,
            eventType = eventType,
            payload = extractNestedPayload(payload, eventType)
        )

        return ResponseEntity.ok(mapOf("status" to "processed"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePayload(rawBody: String): Map<String, Any> {
        val mapper = tools.jackson.databind.json.JsonMapper.builder()
            .findAndAddModules()
            .build()
        return mapper.readValue(rawBody, Map::class.java) as Map<String, Any>
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractNestedPayload(payload: Map<String, Any>, eventType: String): Map<String, Any> {
        return when (eventType) {
            "BOOKING" -> (payload["booking"] as? Map<String, Any>) ?: payload
            else -> payload
        }
    }
}
