package com.stayops.booking.application.service

import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolver
import com.stayops.reservation.domain.model.*
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ForbiddenException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class BookingApplication(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val guestRepository: GuestRepository,
    private val channelRepository: ChannelRepository,
    private val ratePlanRepository: RatePlanRepository,
    private val reservationRepository: ReservationRepository,
    private val paymentRepository: PaymentRepository,
    private val inventoryApplication: RoomInventoryApplication,
    private val paymentGateway: PaymentGateway,
    private val rateResolver: RateResolver
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PENDING_TTL_MINUTES = 15L
    }

    @Transactional
    fun createBooking(
        memberId: String,
        propertyId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        numberOfGuests: Int,
        guestName: String,
        guestPhone: String,
        guestEmail: String?
    ): BookingResult {
        // 1. Property 검증
        val property = propertyRepository.findById(propertyId)
            ?: throw NotFoundException("PROPERTY_NOT_FOUND", "숙소를 찾을 수 없습니다: $propertyId")
        if (!property.isBookable()) {
            throw BusinessException("PROPERTY_NOT_BOOKABLE", "예약 가능한 숙소가 아닙니다: $propertyId")
        }

        // 2. RoomType 검증
        val roomType = roomTypeRepository.findById(roomTypeId)
            ?: throw NotFoundException("ROOM_TYPE_NOT_FOUND", "객실타입을 찾을 수 없습니다: $roomTypeId")

        // 3. Channel (DIRECT) 조회
        val channel = channelRepository.findByPropertyIdAndCode(propertyId, "DIRECT")
            ?: throw NotFoundException("CHANNEL_NOT_FOUND", "DIRECT 채널을 찾을 수 없습니다")

        // 4. 요금 계산
        val dateRange = DateRange.of(checkIn, checkOut)
        val ratePlans = ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
            propertyId, roomTypeId, RatePlanStatus.ACTIVE
        )
        val roomRate = rateResolver.resolveForDateRange(ratePlans, roomType.basePrice, dateRange, "DIRECT")

        // 5. 재고 차감
        dateRange.allDates().forEach { date ->
            inventoryApplication.reserve(propertyId, roomTypeId, date)
        }

        // 6. Guest 조회/생성
        val guest = guestRepository.findByPropertyIdAndPhone(propertyId, guestPhone)
            ?: guestRepository.save(
                Guest.create(
                    id = UUID.randomUUID().toString(),
                    propertyId = propertyId,
                    name = guestName,
                    phone = guestPhone,
                    email = guestEmail
                )
            )

        // 7. Reservation 생성 (PENDING + expiresAt)
        val pricing = ReservationPricing.calculate(
            roomRate = roomRate,
            additionalCharges = Money.ZERO,
            commissionRate = channel.commissionRate
        )
        val reservation = reservationRepository.save(
            Reservation.create(
                id = UUID.randomUUID().toString(),
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                guestId = guest.id,
                guestInfo = GuestInfo(name = guestName, phone = guestPhone, email = guestEmail),
                dateRange = dateRange,
                numberOfGuests = numberOfGuests,
                channel = BookingChannel(channelCode = "DIRECT", commissionRate = channel.commissionRate),
                pricing = pricing,
                memberId = memberId,
                expiresAt = Instant.now().plusSeconds(PENDING_TTL_MINUTES * 60)
            )
        )

        // 8. Payment 생성 (PENDING)
        val payment = paymentRepository.save(
            Payment.create(
                id = UUID.randomUUID().toString(),
                reservationId = reservation.id,
                memberId = memberId,
                amount = pricing.totalAmount
            )
        )

        log.info("예약 생성: reservationId={}, paymentId={}", reservation.id, payment.id)
        return BookingResult(reservation, payment)
    }

    fun getMyReservations(memberId: String): List<Reservation> {
        return reservationRepository.findByMemberId(memberId)
    }

    fun getMyReservation(memberId: String, reservationId: String): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        if (reservation.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약만 조회할 수 있습니다")
        }
        return reservation
    }

    @Transactional
    fun confirmPayment(
        memberId: String,
        reservationId: String,
        paymentKey: String,
        orderId: String,
        amount: BigDecimal
    ): BookingResult {
        // 1. Reservation 조회 + 소유자 검증
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        if (reservation.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약만 결제할 수 있습니다")
        }

        // 2. Payment 조회
        val payment = paymentRepository.findByReservationId(reservationId)
            ?: throw NotFoundException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: $reservationId")

        // 3. Toss Payments 승인
        val confirmResult = paymentGateway.confirm(paymentKey, orderId, amount)

        // 4. Payment 승인
        val approvedPayment = paymentRepository.save(
            payment.approve(
                paymentKey = confirmResult.paymentKey,
                method = confirmResult.method ?: "unknown",
                approvedAt = confirmResult.approvedAt ?: Instant.now()
            )
        )

        // 5. Reservation 확정
        val confirmedReservation = reservationRepository.save(reservation.confirm())

        log.info("결제 승인: reservationId={}, paymentKey={}", reservationId, paymentKey)
        return BookingResult(confirmedReservation, approvedPayment)
    }

    @Transactional
    fun cancelBooking(memberId: String, reservationId: String): BookingResult {
        // 1. Reservation 조회 + 소유자 검증
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        if (reservation.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약만 취소할 수 있습니다")
        }

        // 2. Payment 조회
        val payment = paymentRepository.findByReservationId(reservationId)
            ?: throw NotFoundException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: $reservationId")

        // 3. Toss 환불
        paymentGateway.cancel(payment.paymentKey!!, "고객 요청에 의한 취소")

        // 4. Payment 취소
        val cancelledPayment = paymentRepository.save(payment.cancel())

        // 5. Reservation 취소
        val cancelledReservation = reservationRepository.save(reservation.cancel())

        // 6. 재고 복원
        reservation.dateRange.allDates().forEach { date ->
            inventoryApplication.release(reservation.propertyId, reservation.roomTypeId, date)
        }

        log.info("예약 취소: reservationId={}", reservationId)
        return BookingResult(cancelledReservation, cancelledPayment)
    }
}

data class BookingResult(
    val reservation: Reservation,
    val payment: Payment
)
