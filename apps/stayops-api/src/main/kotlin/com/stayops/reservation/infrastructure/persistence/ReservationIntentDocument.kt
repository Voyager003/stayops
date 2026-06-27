package com.stayops.reservation.infrastructure.persistence

import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Document("reservation_intents")
data class ReservationIntentDocument(
    @Id val id: String,
    val memberId: String,
    val propertyId: String,
    val roomTypeId: String,
    val guestInfo: GuestInfoData,
    val dateRange: DateRangeData,
    val nightCount: Int,
    val numberOfGuests: Int,
    val channel: ReservationChannelData,
    val pricing: PricingData,
    val paymentId: String,
    val holdId: String,
    val reservationId: String?,
    val status: ReservationIntentStatus,
    val expiresAt: Instant,
    @Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    data class GuestInfoData(val name: String, val phone: String, val email: String?)
    data class DateRangeData(val checkIn: String, val checkOut: String)
    data class ReservationChannelData(
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

    fun toDomain(): ReservationIntent = ReservationIntent.reconstitute(
        id = id,
        memberId = memberId,
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        guestInfo = GuestInfo(guestInfo.name, guestInfo.phone, guestInfo.email),
        dateRange = DateRange.of(LocalDate.parse(dateRange.checkIn), LocalDate.parse(dateRange.checkOut)),
        nightCount = nightCount,
        numberOfGuests = numberOfGuests,
        channel = ReservationChannel(channel.channelCode, channel.externalReservationId, channel.commissionRate),
        pricing = ReservationPricing(
            roomRate = Money.of(pricing.roomRateAmount, pricing.roomRateCurrency),
            additionalCharges = Money.of(pricing.additionalChargesAmount, pricing.roomRateCurrency),
            totalAmount = Money.of(pricing.totalAmount, pricing.roomRateCurrency),
            commissionAmount = Money.of(pricing.commissionAmount, pricing.roomRateCurrency),
            netAmount = Money.of(pricing.netAmount, pricing.roomRateCurrency)
        ),
        paymentId = paymentId,
        holdId = holdId,
        reservationId = reservationId,
        status = status,
        expiresAt = expiresAt,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(intent: ReservationIntent): ReservationIntentDocument = ReservationIntentDocument(
            id = intent.id,
            memberId = intent.memberId,
            propertyId = intent.propertyId,
            roomTypeId = intent.roomTypeId,
            guestInfo = GuestInfoData(intent.guestInfo.name, intent.guestInfo.phone, intent.guestInfo.email),
            dateRange = DateRangeData(intent.dateRange.checkIn.toString(), intent.dateRange.checkOut.toString()),
            nightCount = intent.nightCount,
            numberOfGuests = intent.numberOfGuests,
            channel = ReservationChannelData(
                intent.channel.channelCode,
                intent.channel.externalReservationId,
                intent.channel.commissionRate
            ),
            pricing = PricingData(
                roomRateAmount = intent.pricing.roomRate.amount,
                roomRateCurrency = intent.pricing.roomRate.currency,
                additionalChargesAmount = intent.pricing.additionalCharges.amount,
                totalAmount = intent.pricing.totalAmount.amount,
                commissionAmount = intent.pricing.commissionAmount.amount,
                netAmount = intent.pricing.netAmount.amount
            ),
            paymentId = intent.paymentId,
            holdId = intent.holdId,
            reservationId = intent.reservationId,
            status = intent.status,
            expiresAt = intent.expiresAt,
            version = intent.version,
            createdAt = intent.createdAt,
            updatedAt = intent.updatedAt
        )
    }
}
