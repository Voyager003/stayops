package com.stayops.mockota.model

import java.time.Instant

data class ReceivedAri(
    val roomTypeCode: String,
    val date: String,
    val availableCount: Int,
    val idempotencyKey: String,
    val receivedAt: Instant = Instant.now()
)
