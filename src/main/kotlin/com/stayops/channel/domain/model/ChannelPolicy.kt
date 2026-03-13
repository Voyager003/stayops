package com.stayops.channel.domain.model

import java.math.BigDecimal

sealed interface ChannelPolicy {
    val commissionRate: BigDecimal

    data class DirectPolicy(
        override val commissionRate: BigDecimal = BigDecimal.ZERO
    ) : ChannelPolicy

    @ConsistentCopyVisibility
    data class OtaPolicy private constructor(
        override val commissionRate: BigDecimal,
        val webhookSecret: String
    ) : ChannelPolicy {
        companion object {
            fun of(commissionRate: BigDecimal, webhookSecret: String): OtaPolicy {
                require(commissionRate > BigDecimal.ZERO) { "OTA 수수료율은 0보다 커야 합니다: $commissionRate" }
                require(commissionRate <= BigDecimal.ONE) { "수수료율은 1 이하여야 합니다: $commissionRate" }
                require(webhookSecret.isNotBlank()) { "webhookSecret은 공백일 수 없습니다." }
                return OtaPolicy(commissionRate = commissionRate, webhookSecret = webhookSecret)
            }

            fun reconstitute(commissionRate: BigDecimal, webhookSecret: String): OtaPolicy =
                OtaPolicy(commissionRate = commissionRate, webhookSecret = webhookSecret)
        }
    }
}
