package com.stayops.inventory.application.port

interface RoomInventorySyncPort {
    fun syncInventoryForRoomType(propertyId: String, roomTypeId: String)
}
