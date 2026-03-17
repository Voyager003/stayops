package com.stayops.channel.domain.model

data class ChannelConnectionInfo(
    val apiEndpoint: String,
    val apiKey: String?,
    val apiSecret: String?,
    val webhookSecret: String
)
