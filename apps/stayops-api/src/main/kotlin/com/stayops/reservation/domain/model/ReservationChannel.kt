package com.stayops.reservation.domain.model

import java.math.BigDecimal

data class ReservationChannel(
    val channelCode: String,
    val externalReservationId: String? = null,
    val commissionRate: BigDecimal
) {
    init {
        require(channelCode.isNotBlank()) { "채널 코드는 공백일 수 없습니다." }
        require(commissionRate >= BigDecimal.ZERO) { "수수료율은 0 이상이어야 합니다: $commissionRate" }
        require(commissionRate <= BigDecimal.ONE) { "수수료율은 1 이하여야 합니다: $commissionRate" }
    }
}
