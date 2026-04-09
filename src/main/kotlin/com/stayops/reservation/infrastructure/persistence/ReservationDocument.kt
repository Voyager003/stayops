package com.stayops.reservation.infrastructure.persistence

import com.stayops.reservation.domain.model.*
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Document("reservations")
data class ReservationDocument(
    @Id val id: String,
    val propertyId: String,
    val roomTypeId: String,
    val roomId: String?,
    val guestId: String,
    val guestInfo: GuestInfoData,
    val dateRange: DateRangeData,
    val nightCount: Int,
    val numberOfGuests: Int,
    val status: ReservationStatus,
    val channel: BookingChannelData,
    val pricing: PricingData,
    val memberId: String?,
    val expiresAt: Instant?,
    @Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    data class GuestInfoData(val name: String, val phone: String, val email: String?)
    data class DateRangeData(val checkIn: String, val checkOut: String)
    data class BookingChannelData(
        val channelCode: String,
        val externalReservationId: String?,
        val commissionRate: BigDecimal
    )
    data class PricingData(
        val roomRateAmount: BigDecimal,
        val roomRateCurrency: String,
        val additionalChargesAmount: BigDecimal,
        val totalAmount: BigDecimal,
        val commissionAmount: BigDecimal,
        val netAmount: BigDecimal
    )

    fun toDomain(): Reservation = Reservation.reconstitute(
        id = id,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        roomId = roomId,
        guestId = guestId,
        guestInfo = GuestInfo(
            name = guestInfo.name,
            phone = guestInfo.phone,
            email = guestInfo.email
        ),
        dateRange = DateRange.of(
            LocalDate.parse(dateRange.checkIn),
            LocalDate.parse(dateRange.checkOut)
        ),
        nightCount = nightCount,
        numberOfGuests = numberOfGuests,
        status = status,
        channel = BookingChannel(
            channelCode = channel.channelCode,
            externalReservationId = channel.externalReservationId,
            commissionRate = channel.commissionRate
        ),
        pricing = ReservationPricing(
            roomRate = Money.of(pricing.roomRateAmount, pricing.roomRateCurrency),
            additionalCharges = Money.of(pricing.additionalChargesAmount, pricing.roomRateCurrency),
            totalAmount = Money.of(pricing.totalAmount, pricing.roomRateCurrency),
            commissionAmount = Money.of(pricing.commissionAmount, pricing.roomRateCurrency),
            netAmount = Money.of(pricing.netAmount, pricing.roomRateCurrency)
        ),
        memberId = memberId,
        expiresAt = expiresAt,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(reservation: Reservation): ReservationDocument = ReservationDocument(
            id = reservation.id,
            propertyId = reservation.propertyId,
            roomTypeId = reservation.roomTypeId,
            roomId = reservation.roomId,
            guestId = reservation.guestId,
            guestInfo = GuestInfoData(
                name = reservation.guestInfo.name,
                phone = reservation.guestInfo.phone,
                email = reservation.guestInfo.email
            ),
            dateRange = DateRangeData(
                checkIn = reservation.dateRange.checkIn.toString(),
                checkOut = reservation.dateRange.checkOut.toString()
            ),
            nightCount = reservation.nightCount,
            numberOfGuests = reservation.numberOfGuests,
            status = reservation.status,
            channel = BookingChannelData(
                channelCode = reservation.channel.channelCode,
                externalReservationId = reservation.channel.externalReservationId,
                commissionRate = reservation.channel.commissionRate
            ),
            pricing = PricingData(
                roomRateAmount = reservation.pricing.roomRate.amount,
                roomRateCurrency = reservation.pricing.roomRate.currency,
                additionalChargesAmount = reservation.pricing.additionalCharges.amount,
                totalAmount = reservation.pricing.totalAmount.amount,
                commissionAmount = reservation.pricing.commissionAmount.amount,
                netAmount = reservation.pricing.netAmount.amount
            ),
            memberId = reservation.memberId,
            expiresAt = reservation.expiresAt,
            version = reservation.version,
            createdAt = reservation.createdAt,
            updatedAt = reservation.updatedAt
        )
    }
}
