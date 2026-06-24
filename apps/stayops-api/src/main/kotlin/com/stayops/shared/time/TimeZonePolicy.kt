package com.stayops.shared.time

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.ZoneId

@ConfigurationProperties(prefix = "stayops.time")
data class StayopsTimeProperties(
    val defaultZoneId: String = TimeZonePolicy.DEFAULT_ZONE_ID
) {
    fun defaultZone(): ZoneId = TimeZonePolicy.toZoneId(defaultZoneId)
}

object TimeZonePolicy {
    const val DEFAULT_ZONE_ID = "UTC"

    fun toZoneId(zoneId: String): ZoneId = ZoneId.of(zoneId)
}
