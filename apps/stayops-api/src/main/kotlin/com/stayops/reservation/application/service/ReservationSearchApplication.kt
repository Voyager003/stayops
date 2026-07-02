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
import com.stayops.shared.exception.BusinessException
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

data class PropertySearchCriteria(
    val region: String? = null,
    val checkIn: LocalDate? = null,
    val checkOut: LocalDate? = null,
    val guests: Int? = null
)

@Service
class ReservationSearchApplication(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val inventoryRepository: RoomInventoryRepository,
    private val ratePlanRepository: RatePlanRepository,
    private val rateResolverService: RateResolverService
) {

    fun searchProperties(criteria: PropertySearchCriteria = PropertySearchCriteria()): List<Property> {
        validateSearchCriteria(criteria)
        return propertyRepository.findAll()
            .filter { it.isBookable() }
            .filter { matchesRegion(it, criteria.region) }
            .filter { matchesStayConditions(it.id, criteria) }
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
            val inventoriesByDate = inventories.associateBy { it.date }
            val availableCount = dateRange.allDates()
                .map { date -> inventoriesByDate[date]?.availableCount ?: 0 }
                .minOrNull() ?: 0
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

    private fun validateSearchCriteria(criteria: PropertySearchCriteria) {
        if ((criteria.checkIn == null) != (criteria.checkOut == null)) {
            throw BusinessException("INVALID_SEARCH_DATE", "체크인과 체크아웃은 함께 전달해야 합니다")
        }

        if (criteria.checkIn != null && !criteria.checkOut!!.isAfter(criteria.checkIn)) {
            throw BusinessException("INVALID_SEARCH_DATE", "체크아웃은 체크인보다 이후여야 합니다")
        }
    }

    private fun matchesRegion(property: Property, region: String?): Boolean {
        val normalized = region?.trim()
        if (normalized.isNullOrBlank() || normalized == "전체") {
            return true
        }

        return listOf(
            property.address.state,
            property.address.city,
            property.address.street
        ).any { it.contains(normalized, ignoreCase = true) }
    }

    private fun matchesStayConditions(propertyId: String, criteria: PropertySearchCriteria): Boolean {
        val guests = criteria.guests?.takeIf { it > 0 }
        val checkIn = criteria.checkIn
        val checkOut = criteria.checkOut

        if (guests == null && (checkIn == null || checkOut == null)) {
            return true
        }

        return roomTypeRepository.findByPropertyId(propertyId).any { roomType ->
            val fitsGuests = guests == null || roomType.maxOccupancy >= guests
            val hasAvailability =
                if (checkIn != null && checkOut != null) {
                    hasAvailabilityForStay(propertyId, roomType.id, checkIn, checkOut)
                } else {
                    true
                }

            fitsGuests && hasAvailability
        }
    }

    private fun hasAvailabilityForStay(
        propertyId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate
    ): Boolean {
        val dateRange = DateRange.of(checkIn, checkOut)
        val inventoriesByDate = inventoryRepository.findByPropertyIdAndRoomTypeIdAndDateBetween(
            propertyId,
            roomTypeId,
            checkIn,
            checkOut.minusDays(1)
        ).associateBy { it.date }

        return dateRange.allDates().all { date ->
            (inventoriesByDate[date]?.availableCount ?: 0) > 0
        }
    }
}
