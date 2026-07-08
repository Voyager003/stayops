package com.stayops.mockota.dao

import com.stayops.mockota.model.OtaInventory
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

interface OtaInventoryDao : MongoRepository<OtaInventory, String> {
    @Query("{'propertyId': ?0, 'channelCode': ?1, 'roomTypeId': ?2, 'date': {'\$gte': ?3, '\$lte': ?4}}")
    fun findByPropertyIdAndChannelCodeAndRoomTypeIdAndDateRange(
        propertyId: String,
        channelCode: String,
        roomTypeId: String,
        startDate: String,
        endDate: String
    ): List<OtaInventory>
    fun findByPropertyIdAndChannelCodeAndRoomTypeIdAndDate(
        propertyId: String,
        channelCode: String,
        roomTypeId: String,
        date: String
    ): OtaInventory?
    fun findByPropertyIdAndChannelCodeAndAvailableCountGreaterThan(
        propertyId: String,
        channelCode: String,
        count: Int
    ): List<OtaInventory>
}
