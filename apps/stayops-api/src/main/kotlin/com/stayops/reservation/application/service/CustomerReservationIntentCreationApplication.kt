package com.stayops.reservation.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.inventory.application.provided.InventoryHoldService
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.application.required.ReservationPaymentSnapshot
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class CustomerReservationIntentCreationApplication(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val channelRepository: ChannelRepository,
    private val ratePlanRepository: RatePlanRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationIntentRepository: ReservationIntentRepository,
    private val inventoryHoldService: InventoryHoldService,
    private val reservationPaymentService: ReservationPaymentService,
    private val rateResolverService: RateResolverService,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PAYMENT_WAITING_TTL_MINUTES = 15L
        private const val DIRECT_CHANNEL_CODE = "DIRECT"
    }

    @Transactional
    fun createReservationIntent(
        memberId: String,
        propertyId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        numberOfGuests: Int,
        guestName: String,
        guestPhone: String,
        guestEmail: String?
    ): CustomerReservationIntentResult {
        val now = clock.instant()
        val hasDuplicate = reservationRepository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
            memberId,
            roomTypeId,
            checkIn,
            checkOut,
            now
        )
        if (hasDuplicate) {
            throw ConflictException("DUPLICATE_RESERVATION", "이미 동일 조건의 예약이 존재합니다")
        }

        val property = propertyRepository.findById(propertyId)
            ?: throw NotFoundException("PROPERTY_NOT_FOUND", "숙소를 찾을 수 없습니다: $propertyId")
        if (!property.isBookable()) {
            throw BusinessException("PROPERTY_NOT_BOOKABLE", "예약 가능한 숙소가 아닙니다: $propertyId")
        }

        val roomType = roomTypeRepository.findById(roomTypeId)
            ?: throw NotFoundException("ROOM_TYPE_NOT_FOUND", "객실타입을 찾을 수 없습니다: $roomTypeId")
        val channel = channelRepository.findByPropertyIdAndCode(propertyId, DIRECT_CHANNEL_CODE)
            ?: channelRepository.save(Channel.createDirect(idGenerator.generate(), propertyId))
        val dateRange = DateRange.of(checkIn, checkOut)
        val ratePlans = ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
            propertyId,
            roomTypeId,
            RatePlanStatus.ACTIVE
        )
        val roomRate = rateResolverService.resolveForDateRange(
            ratePlans,
            roomType.basePrice,
            dateRange,
            DIRECT_CHANNEL_CODE
        )
        val pricing = ReservationPricing.calculate(
            roomRate = roomRate,
            additionalCharges = Money.ZERO,
            commissionRate = channel.commissionRate
        )

        val reservationIntentId = idGenerator.generate()
        val expiresAt = now.plusSeconds(PAYMENT_WAITING_TTL_MINUTES * 60)
        val hold = inventoryHoldService.hold(
            reservationIntentId = reservationIntentId,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            dateRange = dateRange,
            quantity = 1,
            expiresAt = expiresAt
        )
        val payment = reservationPaymentService.createPendingPaymentForReservationIntent(
            reservationIntentId = reservationIntentId,
            memberId = memberId,
            amount = pricing.totalAmount
        )
        val intent = reservationIntentRepository.save(
            ReservationIntent.create(
                id = reservationIntentId,
                memberId = memberId,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                guestInfo = GuestInfo(name = guestName, phone = guestPhone, email = guestEmail),
                dateRange = dateRange,
                numberOfGuests = numberOfGuests,
                channel = ReservationChannel(channelCode = DIRECT_CHANNEL_CODE, commissionRate = channel.commissionRate),
                pricing = pricing,
                paymentId = payment.id,
                holdId = hold.id,
                expiresAt = expiresAt,
                now = now
            )
        )

        log.info(
            "예약 intent 생성: reservationIntentId={}, paymentId={}, holdId={}",
            intent.id,
            payment.id,
            hold.id
        )
        return CustomerReservationIntentResult(intent, payment)
    }
}

data class CustomerReservationIntentResult(
    val intent: ReservationIntent,
    val payment: ReservationPaymentSnapshot
)
