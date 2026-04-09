package com.stayops.booking.application.service

import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.payment.domain.model.Payment
import com.stayops.payment.domain.repository.PaymentRepository
import com.stayops.payment.domain.service.PaymentConfirmResult
import com.stayops.payment.domain.service.PaymentGateway
import com.stayops.payment.domain.service.PaymentGatewayException
import com.stayops.property.domain.repository.PropertyRepository
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolver
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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

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
    private val rateResolver: RateResolver,
    private val clock: Clock,
    private val idGenerator: IdGenerator
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
        // 0. 중복 예약 검증
        val activeStatuses = listOf(ReservationStatus.PENDING, ReservationStatus.CONFIRMED)
        val hasDuplicate = reservationRepository.existsByMemberIdAndRoomTypeIdAndCheckInAndCheckOutAndStatusIn(
            memberId, roomTypeId, checkIn, checkOut, activeStatuses
        )
        if (hasDuplicate) {
            throw ConflictException("DUPLICATE_BOOKING", "이미 동일 조건의 예약이 존재합니다")
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
        val roomRate = rateResolver.resolveForDateRange(ratePlans, roomType.basePrice, dateRange, "DIRECT")

        // 5. 재고 차감
        dateRange.allDates().forEach { date ->
            inventoryApplication.reserve(propertyId, roomTypeId, date)
        }

        // 6. Guest 조회/생성
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

        // 7. Reservation 생성 (PENDING + expiresAt)
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
                channel = BookingChannel(channelCode = "DIRECT", commissionRate = channel.commissionRate),
                pricing = pricing,
                memberId = memberId,
                expiresAt = clock.instant().plusSeconds(PENDING_TTL_MINUTES * 60)
            )
        )

        // 8. Payment 생성 (PENDING)
        val payment = paymentRepository.save(
            Payment.create(
                id = idGenerator.generate(),
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

        // 2-1. 멱등성: 이미 CONFIRMED 상태이면 기존 결과 반환
        if (reservation.status == ReservationStatus.CONFIRMED) {
            return BookingResult(reservation, payment)
        }

        // 3. 만료 검증 — expiresAt이 지났으면 결제 불가
        if (reservation.expiresAt != null && clock.instant().isAfter(reservation.expiresAt)) {
            throw BusinessException("RESERVATION_EXPIRED", "결제 가능 시간이 만료되었습니다")
        }

        // 4. orderId 검증 — 클라이언트가 보낸 orderId와 DB orderId 비교
        if (payment.orderId != orderId) {
            throw BusinessException(
                "ORDER_ID_MISMATCH",
                "주문 ID가 일치하지 않습니다. 예상: ${payment.orderId}, 요청: $orderId"
            )
        }

        // 5. 금액 검증 — 클라이언트가 보낸 금액과 DB 금액 비교
        if (payment.amount.amount.compareTo(amount) != 0) {
            throw BusinessException(
                "AMOUNT_MISMATCH",
                "결제 금액이 일치하지 않습니다. 예상: ${payment.amount.amount}, 요청: $amount"
            )
        }

        // 6. Toss Payments 승인
        val confirmResult: PaymentConfirmResult
        try {
            confirmResult = paymentGateway.confirm(paymentKey, orderId, amount)
        } catch (e: PaymentGatewayException.AlreadyProcessed) {
            log.warn("이미 처리된 결제, inquire로 상태 확인: paymentKey={}", paymentKey)
            val inquiry = paymentGateway.inquire(paymentKey)
            if (inquiry.status == "DONE") {
                val approvedPayment = paymentRepository.save(
                    payment.approve(paymentKey = paymentKey, method = "unknown", approvedAt = clock.instant())
                )
                val confirmedReservation = reservationRepository.save(reservation.confirm())
                return BookingResult(confirmedReservation, approvedPayment)
            }
            paymentRepository.save(payment.fail("이미 처리된 결제이나 상태가 DONE이 아님: ${inquiry.status}"))
            throw BusinessException("PAYMENT_ALREADY_PROCESSED", "결제가 이미 처리되었으나 정상 완료되지 않았습니다: ${inquiry.status}")
        } catch (e: PaymentGatewayException.PaymentDeclined) {
            log.warn("결제 거절: paymentKey={}, code={}", paymentKey, e.code)
            paymentRepository.save(payment.fail(e.reason))
            throw BusinessException("PAYMENT_DECLINED", "결제가 거절되었습니다: ${e.reason}")
        } catch (e: PaymentGatewayException.ProviderError) {
            log.error("PG사 시스템 오류: paymentKey={}, code={}", paymentKey, e.code)
            throw e // Payment 상태 유지 (PENDING), 재시도 가능
        }

        // 7. Payment 승인
        val approvedPayment = paymentRepository.save(
            payment.approve(
                paymentKey = confirmResult.paymentKey,
                method = confirmResult.method ?: "unknown",
                approvedAt = confirmResult.approvedAt ?: clock.instant()
            )
        )

        // 8. Reservation 확정
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

        // 3. 상태에 따라 분기
        val cancelledReservation: Reservation
        val cancelledPayment: Payment

        if (reservation.status == ReservationStatus.PENDING) {
            // PENDING: 결제 전이므로 Toss 환불 불필요
            cancelledReservation = reservationRepository.save(reservation.cancelPending())
            cancelledPayment = paymentRepository.save(payment.fail("고객 요청에 의한 취소"))
        } else {
            // CONFIRMED: Toss 환불 필요
            cancelledReservation = reservationRepository.save(reservation.cancel())
            cancelledPayment = try {
                paymentGateway.cancel(payment.paymentKey!!, "고객 요청에 의한 취소")
                paymentRepository.save(payment.cancel())
            } catch (e: PaymentGatewayException) {
                log.error("Toss 환불 실패: reservationId={}, paymentKey={}", reservationId, payment.paymentKey, e)
                paymentRepository.save(payment.failCancel("환불 실패: ${e.message}"))
            }
        }

        // 4. 재고 복원
        reservation.dateRange.allDates().forEach { date ->
            inventoryApplication.release(reservation.propertyId, reservation.roomTypeId, date)
        }

        log.info("예약 취소: reservationId={}, 이전상태={}", reservationId, reservation.status)
        return BookingResult(cancelledReservation, cancelledPayment)
    }
}

data class BookingResult(
    val reservation: Reservation,
    val payment: Payment
)
