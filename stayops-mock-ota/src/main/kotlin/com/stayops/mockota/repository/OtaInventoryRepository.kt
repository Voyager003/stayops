package com.stayops.mockota.repository

import com.stayops.mockota.model.OtaInventory
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

interface OtaInventoryRepository : MongoRepository<OtaInventory, String> {
    @Query("{'roomTypeId': ?0, 'date': {'\$gte': ?1, '\$lte': ?2}}")
    fun findByRoomTypeIdAndDateRange(roomTypeId: String, startDate: String, endDate: String): List<OtaInventory>
    fun findByRoomTypeIdAndDate(roomTypeId: String, date: String): OtaInventory?
}
