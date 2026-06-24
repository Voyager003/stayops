package com.stayops.rate.api.dto

import com.stayops.rate.domain.model.RatePlanType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class CreateRatePlanRequest(
    @field:NotBlank val roomTypeId: String,
    @field:NotBlank val name: String,
    @field:NotNull val type: RatePlanType,
    val dateRangeStart: LocalDate? = null,
    val dateRangeEnd: LocalDate? = null,
    val dayOfWeekRules: List<DayOfWeekRuleRequest>? = null,
    val channelCode: String? = null,
    @field:Min(1) val price: Long,
    @field:Min(1) val priority: Int
)

data class DayOfWeekRuleRequest(
    val daysOfWeek: List<String>,
    val price: Long
)

data class UpdateRatePlanRequest(
    @field:NotBlank val name: String,
    val dateRangeStart: LocalDate? = null,
    val dateRangeEnd: LocalDate? = null,
    val dayOfWeekRules: List<DayOfWeekRuleRequest>? = null,
    val channelCode: String? = null,
    @field:Min(1) val price: Long,
    @field:Min(1) val priority: Int
)
