package com.stayops.channel.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

data class CreateChannelRequest(
    @field:NotBlank
    val code: String,
    @field:NotBlank
    val name: String,
    @field:DecimalMin("0.01")
    val commissionRate: BigDecimal,
    @field:NotBlank
    val apiEndpoint: String,
    val apiKey: String? = null,
    val apiSecret: String? = null,
    @field:NotBlank
    val webhookSecret: String
)
