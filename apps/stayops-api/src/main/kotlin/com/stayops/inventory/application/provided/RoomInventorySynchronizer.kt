package com.stayops.inventory.application.provided

interface RoomInventorySynchronizer {
    fun syncInventoryForRoomType(propertyId: String, roomTypeId: String)
}
