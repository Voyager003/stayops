package com.stayops.reservation.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class CustomerReservationCreationApplication(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val guestRepository: GuestRepository,
    private val channelRepository: ChannelRepository,
    private val ratePlanRepository: RatePlanRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationPaymentPort: ReservationPaymentService,
    private val rateResolverService: RateResolverService,
    private val clock: Clock,
    private val idGenerator: IdGenerator
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PENDING_TTL_MINUTES = 15L
    }

    @Transactional
    fun createReservation(
        memberId: String,
        propertyId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        numberOfGuests: Int,
        guestName: String,
        guestPhone: String,
        guestEmail: String?
    ): CustomerReservationResult {
        if (MDC.get("experimentId") != null) {
            log.info(
                "부하 테스트 예약 생성 시작: propertyId={}, roomTypeId={}, checkIn={}, checkOut={}",
                propertyId, roomTypeId, checkIn, checkOut
            )
        }

        val hasDuplicate = reservationRepository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
            memberId, roomTypeId, checkIn, checkOut, clock.instant()
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

        val channel = channelRepository.findByPropertyIdAndCode(propertyId, "DIRECT")
            ?: channelRepository.save(Channel.createDirect(idGenerator.generate(), propertyId))

        val dateRange = DateRange.of(checkIn, checkOut)
        val ratePlans = ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
            propertyId, roomTypeId, RatePlanStatus.ACTIVE
        )
        val roomRate = rateResolverService.resolveForDateRange(ratePlans, roomType.basePrice, dateRange, "DIRECT")

        val guest = guestRepository.findByPropertyIdAndPhone(propertyId, guestPhone)
            ?: guestRepository.save(
                Guest.create(
                    id = idGenerator.generate(),
                    propertyId = propertyId,
                    name = guestName,
                    phone = guestPhone,
                    email = guestEmail
                )
            )

        val pricing = ReservationPricing.calculate(
            roomRate = roomRate,
            additionalCharges = Money.ZERO,
            commissionRate = channel.commissionRate
        )
        val reservation = reservationRepository.save(
            Reservation.create(
                id = idGenerator.generate(),
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                guestId = guest.id,
                guestInfo = GuestInfo(name = guestName, phone = guestPhone, email = guestEmail),
                dateRange = dateRange,
                numberOfGuests = numberOfGuests,
                channel = ReservationChannel(channelCode = "DIRECT", commissionRate = channel.commissionRate),
                pricing = pricing,
                memberId = memberId,
                expiresAt = clock.instant().plusSeconds(PENDING_TTL_MINUTES * 60)
            )
        )

        val payment = reservationPaymentPort.createPendingPayment(
            reservationId = reservation.id,
            memberId = memberId,
            amount = pricing.totalAmount
        )

        if (MDC.get("experimentId") != null) {
            log.info("부하 테스트 예약 생성 성공: reservationId={}, paymentId={}", reservation.id, payment.id)
        } else {
            log.info("예약 생성: reservationId={}, paymentId={}", reservation.id, payment.id)
        }
        return CustomerReservationResult(reservation, payment)
    }
}
