package com.stayops.channel.application.dto

import java.time.LocalDate

data class AvailabilityPayload(
    val roomTypeId: String,
    val date: LocalDate,
    val availableCount: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "roomTypeId" to roomTypeId,
        "date" to date.toString(),
        "availableCount" to availableCount
    )
}
