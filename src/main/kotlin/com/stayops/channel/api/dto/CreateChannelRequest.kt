package com.stayops.channel.api.dto

import java.math.BigDecimal

data class CreateChannelRequest(
    val code: String,
    val name: String,
    val commissionRate: BigDecimal,
    val apiEndpoint: String,
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val webhookSecret: String
)
