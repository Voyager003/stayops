package com.stayops.channel.application.required

interface ChannelAvailabilityPublisher {
    fun pushAvailability(
        endpoint: String,
        apiKey: String?,
        externalRoomTypeCode: String,
        payload: Map<String, Any>,
        idempotencyKey: String
    ): SyncResult
}

data class SyncResult(val success: Boolean, val errorMessage: String? = null)
