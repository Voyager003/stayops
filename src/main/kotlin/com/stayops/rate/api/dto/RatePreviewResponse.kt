package com.stayops.rate.api.dto

import com.stayops.rate.application.service.RatePlanApplication

data class RatePreviewResponse(
    val date: String,
    val price: Long
) {
    companion object {
        fun from(dailyRate: RatePlanApplication.DailyRate) = RatePreviewResponse(
            date = dailyRate.date.toString(),
            price = dailyRate.price.amount.toLong()
        )
    }
}
