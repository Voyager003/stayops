package com.stayops.channel.api.dto

import java.math.BigDecimal

data class UpdateChannelRequest(
    val name: String? = null,
    val commissionRate: BigDecimal? = null,
    val apiEndpoint: String? = null,
    val apiKey: String? = null,
    val apiSecret: String? = null,
    val webhookSecret: String? = null
)
