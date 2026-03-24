package com.stayops.booking.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolver
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.model.RoomTypeStatus
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class BookingSearchApplication(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val inventoryRepository: RoomInventoryRepository,
    private val ratePlanRepository: RatePlanRepository,
    private val rateResolver: RateResolver
) {

    fun searchProperties(): List<Property> {
        return propertyRepository.findAll().filter { it.isBookable() }
    }

    fun getProperty(propertyId: String): Property {
        val property = propertyRepository.findById(propertyId)
            ?: throw NotFoundException("PROPERTY_NOT_FOUND", "숙소를 찾을 수 없습니다: $propertyId")
        if (!property.isBookable()) {
            throw NotFoundException("PROPERTY_NOT_BOOKABLE", "예약 가능한 숙소가 아닙니다: $propertyId")
        }
        return property
    }

    fun searchRoomTypes(propertyId: String): List<RoomType> {
        getProperty(propertyId)
        return roomTypeRepository.findByPropertyId(propertyId)
            .filter { it.status == RoomTypeStatus.ACTIVE }
    }

    fun getAvailability(
        propertyId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate
    ): List<RoomInventory> {
        getProperty(propertyId)
        return inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
            propertyId, roomTypeId, checkIn, checkOut.minusDays(1)
        )
    }

    fun getRatePreview(
        propertyId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate
    ): Money {
        getProperty(propertyId)
        val roomType = roomTypeRepository.findById(roomTypeId)
            ?: throw NotFoundException("ROOM_TYPE_NOT_FOUND", "객실타입을 찾을 수 없습니다: $roomTypeId")

        val dateRange = DateRange.of(checkIn, checkOut)
        val ratePlans = ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
            propertyId, roomTypeId, RatePlanStatus.ACTIVE
        )

        return rateResolver.resolveForDateRange(ratePlans, roomType.basePrice, dateRange, "DIRECT")
    }
}
