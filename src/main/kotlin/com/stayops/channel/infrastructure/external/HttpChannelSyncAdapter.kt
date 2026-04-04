package com.stayops.channel.infrastructure.external

import com.stayops.channel.domain.service.ChannelSyncAdapter
import com.stayops.channel.domain.service.SyncResult
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class HttpChannelSyncAdapter : ChannelSyncAdapter {

    private val restClient = RestClient.create()

    override fun pushAvailability(
        endpoint: String,
        apiKey: String?,
        externalRoomTypeCode: String,
        payload: Map<String, Any>,
        idempotencyKey: String
    ): SyncResult {
        val body = payload.toMutableMap()
        body["roomTypeCode"] = externalRoomTypeCode

        return try {
            val requestSpec = restClient.post()
                .uri("$endpoint/api/v1/ari/availability")
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", idempotencyKey)

            if (apiKey != null) {
                requestSpec.header("X-API-Key", apiKey)
            }

            requestSpec.body(body)
                .retrieve()
                .toBodilessEntity()

            SyncResult(success = true)
        } catch (e: RestClientException) {
            SyncResult(success = false, errorMessage = e.message ?: "HTTP call failed")
        }
    }
}
