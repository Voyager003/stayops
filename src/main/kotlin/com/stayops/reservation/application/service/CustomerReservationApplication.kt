package com.stayops.reservation.application.service

import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.reservation.application.port.ReservationPaymentPort
import com.stayops.reservation.application.port.ReservationPaymentSnapshot
import com.stayops.reservation.domain.model.*
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.ForbiddenException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Service
class CustomerReservationApplication(
    private val propertyRepository: PropertyRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val guestRepository: GuestRepository,
    private val channelRepository: ChannelRepository,
    private val ratePlanRepository: RatePlanRepository,
    private val reservationRepository: ReservationRepository,
    private val reservationPaymentPort: ReservationPaymentPort,
    private val inventoryReservationPort: InventoryReservationPort,
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

        // 0. 중복 예약 검증
        val hasDuplicate = reservationRepository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
            memberId, roomTypeId, checkIn, checkOut, clock.instant()
        )
        if (hasDuplicate) {
            throw ConflictException("DUPLICATE_RESERVATION", "이미 동일 조건의 예약이 존재합니다")
        }

        // 1. Property 검증
        val property = propertyRepository.findById(propertyId)
            ?: throw NotFoundException("PROPERTY_NOT_FOUND", "숙소를 찾을 수 없습니다: $propertyId")
        if (!property.isBookable()) {
            throw BusinessException("PROPERTY_NOT_BOOKABLE", "예약 가능한 숙소가 아닙니다: $propertyId")
        }

        // 2. RoomType 검증
        val roomType = roomTypeRepository.findById(roomTypeId)
            ?: throw NotFoundException("ROOM_TYPE_NOT_FOUND", "객실타입을 찾을 수 없습니다: $roomTypeId")

        // 3. Channel (DIRECT) 조회 — 없으면 자동 생성 (기존 Property 호환)
        val channel = channelRepository.findByPropertyIdAndCode(propertyId, "DIRECT")
            ?: channelRepository.save(
                com.stayops.channel.domain.model.Channel.createDirect(idGenerator.generate(), propertyId)
            )

        // 4. 요금 계산
        val dateRange = DateRange.of(checkIn, checkOut)
        val ratePlans = ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
            propertyId, roomTypeId, RatePlanStatus.ACTIVE
        )
        val roomRate = rateResolverService.resolveForDateRange(ratePlans, roomType.basePrice, dateRange, "DIRECT")

        // 5. Guest 조회/생성
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

        // 6. Reservation 생성 (PENDING + expiresAt)
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

        // 7. Payment 생성 (PENDING)
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

    @Transactional(readOnly = true)
    fun getMyReservations(memberId: String): List<CustomerReservationReadResult> {
        val reservations = reservationRepository.findByMemberId(memberId)
        val paymentsByReservationId = reservationPaymentPort.findByMemberId(memberId)
            .associateBy { it.reservationId }
        return reservations.map { reservation ->
            CustomerReservationReadResult(reservation, paymentsByReservationId[reservation.id])
        }
    }

    @Transactional(readOnly = true)
    fun getMyReservation(memberId: String, reservationId: String): CustomerReservationReadResult {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        if (reservation.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약만 조회할 수 있습니다")
        }
        return CustomerReservationReadResult(
            reservation = reservation,
            payment = reservationPaymentPort.findByReservationId(reservationId)
        )
    }

    @Transactional
    fun confirmPayment(
        memberId: String,
        reservationId: String,
        paymentKey: String,
        orderId: String,
        amount: BigDecimal
    ): CustomerReservationResult {
        // 1. Reservation 조회 + 소유자 검증
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        if (reservation.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약만 결제할 수 있습니다")
        }

        // 2. Payment 조회
        val payment = reservationPaymentPort.findByReservationId(reservationId)
            ?: throw NotFoundException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: $reservationId")

        // 2-1. 멱등성: 이미 CONFIRMED 상태이면 기존 결과 반환
        if (reservation.status == ReservationStatus.CONFIRMED) {
            return CustomerReservationResult(reservation, payment)
        }

        // 3. 만료 검증 — expiresAt이 지났으면 결제 불가
        if (reservation.expiresAt != null && clock.instant().isAfter(reservation.expiresAt)) {
            throw BusinessException("RESERVATION_EXPIRED", "결제 가능 시간이 만료되었습니다")
        }

        // 4. Payment 승인 요청 상태 저장 + 결제 승인 Outbox 생성은 Payment adapter에 위임
        val savedPayment = reservationPaymentPort.requestConfirm(
            reservationId = reservation.id,
            memberId = memberId,
            paymentKey = paymentKey,
            orderId = orderId,
            amount = amount
        )

        log.info("결제 승인 요청 접수: reservationId={}, paymentId={}", reservationId, savedPayment.id)
        return CustomerReservationResult(reservation, savedPayment)
    }

    @Transactional
    fun cancelReservation(memberId: String, reservationId: String): CustomerReservationResult {
        // 1. Reservation 조회 + 소유자 검증
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        if (reservation.memberId != memberId) {
            throw ForbiddenException("ACCESS_DENIED", "본인의 예약만 취소할 수 있습니다")
        }

        // 2. 상태에 따라 분기
        val cancelledReservation: Reservation
        val cancelledPayment: ReservationPaymentSnapshot

        if (reservation.status == ReservationStatus.PENDING) {
            // PENDING: 결제 전이므로 Toss 환불 불필요
            cancelledReservation = reservationRepository.save(reservation.cancelPending())
            cancelledPayment = reservationPaymentPort.cancelPendingByCustomerRequest(reservation.id)
        } else {
            // CONFIRMED: 결제 취소 요청을 Outbox로 남기고 worker가 PG 취소를 처리
            cancelledReservation = reservationRepository.save(reservation.cancel())
            cancelledPayment = reservationPaymentPort.requestCancelByCustomerRequest(
                reservationId = reservation.id,
                memberId = memberId
            )

            // 4. 확정 예약은 이미 결제 승인 worker에서 재고가 차감되었으므로 취소 시 복원한다.
            reservation.dateRange.allDates().forEach { date ->
                inventoryReservationPort.release(reservation.propertyId, reservation.roomTypeId, date)
            }
        }

        log.info("예약 취소: reservationId={}, 이전상태={}", reservationId, reservation.status)
        return CustomerReservationResult(cancelledReservation, cancelledPayment)
    }
}

data class CustomerReservationResult(
    val reservation: Reservation,
    val payment: ReservationPaymentSnapshot
)

data class CustomerReservationReadResult(
    val reservation: Reservation,
    val payment: ReservationPaymentSnapshot?
)
