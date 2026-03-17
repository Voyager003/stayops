package com.stayops.channel.domain.service

interface ChannelSyncAdapter {
    fun pushAvailability(
        endpoint: String,
        apiKey: String?,
        externalRoomTypeCode: String,
        payload: Map<String, Any>,
        idempotencyKey: String
    ): SyncResult
}

data class SyncResult(val success: Boolean, val errorMessage: String? = null)
