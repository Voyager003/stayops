package com.stayops.booking.api.dto

import com.stayops.booking.application.service.PropertyOffer
import com.stayops.inventory.domain.model.RoomInventory
import com.stayops.property.domain.model.Property
import com.stayops.property.domain.model.PropertyStatus
import com.stayops.property.domain.model.PropertyType
import com.stayops.room.domain.model.RoomType
import com.stayops.shared.domain.Money
import java.math.BigDecimal
import java.time.LocalDate

data class PropertySearchResponse(
    val id: String,
    val name: String,
    val type: PropertyType,
    val address: AddressResponse,
    val description: String,
    val status: PropertyStatus,
    val timezone: String,
    val currency: String
) {
    companion object {
        fun from(property: Property) = PropertySearchResponse(
            id = property.id,
            name = property.name,
            type = property.type,
            address = AddressResponse(
                street = property.address.street,
                city = property.address.city,
                state = property.address.state,
                zipCode = property.address.zipCode,
                country = property.address.country
            ),
            description = property.description,
            status = property.status,
            timezone = property.timezone,
            currency = property.currency
        )
    }
}

data class AddressResponse(
    val street: String,
    val city: String,
    val state: String,
    val zipCode: String,
    val country: String
)

data class RoomTypeSearchResponse(
    val id: String,
    val name: String,
    val description: String,
    val maxOccupancy: Int,
    val basePrice: BigDecimal,
    val amenities: List<String>
) {
    companion object {
        fun from(roomType: RoomType) = RoomTypeSearchResponse(
            id = roomType.id,
            name = roomType.name,
            description = roomType.description,
            maxOccupancy = roomType.maxOccupancy,
            basePrice = roomType.basePrice.amount,
            amenities = roomType.amenities
        )
    }
}

data class PropertyOfferResponse(
    val roomTypeId: String,
    val name: String,
    val description: String,
    val maxOccupancy: Int,
    val amenities: List<String>,
    val basePrice: BigDecimal,
    val availableCount: Int,
    val fitsGuests: Boolean,
    val rateQuote: RatePreviewResponse
) {
    companion object {
        fun from(offer: PropertyOffer) = PropertyOfferResponse(
            roomTypeId = offer.roomType.id,
            name = offer.roomType.name,
            description = offer.roomType.description,
            maxOccupancy = offer.roomType.maxOccupancy,
            amenities = offer.roomType.amenities,
            basePrice = offer.roomType.basePrice.amount,
            availableCount = offer.availableCount,
            fitsGuests = offer.fitsGuests,
            rateQuote = RatePreviewResponse.of(offer.rateQuote, offer.checkIn, offer.checkOut)
        )
    }
}

data class AvailabilityResponse(
    val date: LocalDate,
    val availableCount: Int
) {
    companion object {
        fun from(inventory: RoomInventory) = AvailabilityResponse(
            date = inventory.date,
            availableCount = inventory.availableCount
        )
    }
}

data class RatePreviewResponse(
    val totalAmount: BigDecimal,
    val currency: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val nights: Int
) {
    companion object {
        fun of(amount: Money, checkIn: LocalDate, checkOut: LocalDate) = RatePreviewResponse(
            totalAmount = amount.amount,
            currency = amount.currency,
            checkIn = checkIn,
            checkOut = checkOut,
            nights = (checkOut.toEpochDay() - checkIn.toEpochDay()).toInt()
        )
    }
}
