package com.stayops.rate.api.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

data class UpdateRatePlanRequest(
    @field:NotBlank val name: String,
    val dateRangeStart: LocalDate? = null,
    val dateRangeEnd: LocalDate? = null,
    val dayOfWeekRules: List<DayOfWeekRuleRequest>? = null,
    val channelCode: String? = null,
    @field:Min(1) val price: Long,
    @field:Min(1) val priority: Int
)
