package com.stayops.mockota.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "ota_inventory")
@CompoundIndex(def = "{'roomTypeId': 1, 'date': 1}", unique = true)
data class OtaInventory(
    @Id val id: String? = null,
    val roomTypeId: String,
    val date: String,
    val availableCount: Int,
    val updatedAt: Instant = Instant.now()
)
