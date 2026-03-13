package com.stayops.rate.domain.model

import com.stayops.shared.domain.Money
import java.time.DayOfWeek

data class DayOfWeekRate(
    val daysOfWeek: Set<DayOfWeek>,
    val price: Money
)
