package com.stayops.channel.api.dto

import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal

data class UpdateChannelRequest(
    val name: String? = null,
    @field:DecimalMin("0.01")
    val commissionRate: BigDecimal? = null
)
