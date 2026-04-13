package com.stayops.reservation.application.service

import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.inventory.domain.repository.RoomInventoryRepository
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.NotFoundException
import org.springframework.stereotype.Service
import java.time.LocalDate

data class ReservationOffer(
    val roomType: RoomType,
    val availableCount: Int,
    val fitsGuests: Boolean,
    val rateQuote: Money,
    val checkIn: LocalDate,
    val checkOut: LocalDate
)

@Service
class ReservationSearchApplication(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val inventoryRepository: RoomInventoryRepository,
    private val ratePlanRepository: RatePlanRepository,
    private val rateResolverService: RateResolverService
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
    }

    fun getReservationOffers(
        propertyId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int
    ): List<ReservationOffer> {
        getProperty(propertyId)
        val dateRange = DateRange.of(checkIn, checkOut)

        return roomTypeRepository.findByPropertyId(propertyId).map { roomType ->
            val inventories = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
                propertyId,
                roomType.id,
                checkIn,
                checkOut.minusDays(1)
            )
            val ratePlans = ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
                propertyId,
                roomType.id,
                RatePlanStatus.ACTIVE
            )
            val availableCount = inventories.minOfOrNull { it.availableCount } ?: 0
            ReservationOffer(
                roomType = roomType,
                availableCount = availableCount,
                fitsGuests = roomType.maxOccupancy >= guests,
                rateQuote = rateResolverService.resolveForDateRange(ratePlans, roomType.basePrice, dateRange, "DIRECT"),
                checkIn = checkIn,
                checkOut = checkOut
            )
        }
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

        return rateResolverService.resolveForDateRange(ratePlans, roomType.basePrice, dateRange, "DIRECT")
    }
}
