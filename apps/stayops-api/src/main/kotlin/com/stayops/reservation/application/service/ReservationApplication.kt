package com.stayops.reservation.application.service

import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.reservation.application.port.ReservationPaymentPort
import com.stayops.reservation.application.port.ReservationPaymentStatus
import com.stayops.reservation.domain.event.ReservationCancelled
import com.stayops.reservation.domain.event.ReservationCheckedOut
import com.stayops.reservation.domain.event.ReservationCreated
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.model.RoomStatus
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.PagedResult
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class ReservationApplication(
    private val reservationRepository: ReservationRepository,
    private val roomTypeRepository: RoomTypeRepository,
    private val channelRepository: ChannelRepository,
    private val ratePlanRepository: RatePlanRepository,
    private val guestRepository: GuestRepository,
    private val inventoryReservationPort: InventoryReservationPort,
    private val roomRepository: RoomRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val rateResolverService: RateResolverService,
    private val reservationPaymentPort: ReservationPaymentPort,
    private val idGenerator: IdGenerator,
    private val clock: Clock
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createReservation(
        propertyId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        numberOfGuests: Int,
        guestId: String,
        guestName: String,
        guestPhone: String,
        guestEmail: String?,
        channelCode: String,
        externalReservationId: String? = null
    ): Reservation {
        // 1. RoomType 존재 확인
        val roomType = roomTypeRepository.findById(roomTypeId)
            ?: throw NotFoundException("ROOM_TYPE_NOT_FOUND", "객실타입을 찾을 수 없습니다: $roomTypeId")

        // 2. Channel 유효성 확인 + commissionRate 조회
        val channel = channelRepository.findByPropertyIdAndCode(propertyId, channelCode)
            ?: throw NotFoundException("CHANNEL_NOT_FOUND", "채널을 찾을 수 없습니다: $channelCode")

        // 3. RateResolverService로 날짜별 요금 산출
        val dateRange = DateRange.of(checkIn, checkOut)
        val ratePlans = ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(
            propertyId, roomTypeId, RatePlanStatus.ACTIVE
        )
        val roomRate = rateResolverService.resolveForDateRange(ratePlans, roomType.basePrice, dateRange, channelCode)

        // 4. 날짜별 재고 차감
        dateRange.allDates().forEach { date ->
            inventoryReservationPort.reserve(propertyId, roomTypeId, date)
        }

        // 5. Guest 조회
        val guest = guestRepository.findById(guestId)
            ?: throw NotFoundException("GUEST_NOT_FOUND", "고객을 찾을 수 없습니다: $guestId")

        // 6. Reservation 생성 + 저장
        val pricing = ReservationPricing.calculate(
            roomRate = roomRate,
            additionalCharges = Money.ZERO,
            commissionRate = channel.commissionRate
        )

        val reservation = Reservation.create(
            id = idGenerator.generate(),
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            guestId = guestId,
            guestInfo = GuestInfo(name = guestName, phone = guestPhone, email = guestEmail),
            dateRange = dateRange,
            numberOfGuests = numberOfGuests,
            channel = ReservationChannel(
                channelCode = channelCode,
                externalReservationId = externalReservationId,
                commissionRate = channel.commissionRate
            ),
            pricing = pricing
        )

        val saved = reservationRepository.save(reservation)

        // 7. ReservationCreated 이벤트 발행
        eventPublisher.publishEvent(
            ReservationCreated(
                reservationId = saved.id,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                dateRange = dateRange,
                channelCode = channelCode
            )
        )

        log.info("예약 생성: id={}, propertyId={}, channelCode={}", saved.id, propertyId, channelCode)
        return saved
    }

    fun getReservation(propertyId: String, reservationId: String): Reservation {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw NotFoundException("RESERVATION_NOT_FOUND", "예약을 찾을 수 없습니다: $reservationId")
        require(reservation.propertyId == propertyId) { "예약이 해당 숙소에 속하지 않습니다." }
        return reservation
    }

    fun getReservations(propertyId: String): List<Reservation> =
        reservationRepository.findByPropertyId(propertyId)

    fun getReservationsByStatus(propertyId: String, status: ReservationStatus): List<Reservation> =
        reservationRepository.findByPropertyIdAndStatus(propertyId, status)

    fun searchReservations(
        propertyId: String,
        criteria: ReservationSearchCriteria,
        page: Int,
        size: Int
    ): PagedResult<Reservation> = reservationRepository.search(propertyId, criteria, page, size)

    fun searchReservationsByPropertyIds(
        propertyIds: List<String>,
        criteria: ReservationSearchCriteria,
        page: Int,
        size: Int
    ): PagedResult<Reservation> = reservationRepository.searchByPropertyIds(propertyIds, criteria, page, size)

    fun confirmReservation(propertyId: String, reservationId: String): Reservation {
        val reservation = getReservation(propertyId, reservationId)
        requireApprovedPaymentForCustomerReservation(reservation)
        return reservationRepository.save(reservation.confirm())
    }

    @Transactional
    fun cancelReservation(propertyId: String, reservationId: String): Reservation {
        val reservation = getReservation(propertyId, reservationId)
        val shouldReleaseInventory = shouldReleaseInventoryOnCancel(reservation)
        val cancelled = if (reservation.status == ReservationStatus.PENDING) {
            reservation.cancelPending()
        } else {
            reservation.cancel()
        }
        val saved = reservationRepository.save(cancelled)

        if (shouldReleaseInventory) {
            reservation.dateRange.allDates().forEach { date ->
                inventoryReservationPort.release(reservation.propertyId, reservation.roomTypeId, date)
            }

            eventPublisher.publishEvent(
                ReservationCancelled(
                    reservationId = saved.id,
                    propertyId = reservation.propertyId,
                    roomTypeId = reservation.roomTypeId,
                    dateRange = reservation.dateRange
                )
            )
        }

        log.info("예약 취소: id={}", saved.id)
        return saved
    }

    private fun requireApprovedPaymentForCustomerReservation(reservation: Reservation) {
        if (reservation.memberId == null) {
            return
        }

        val payment = reservationPaymentPort.findByReservationId(reservation.id)
            ?: throw BusinessException("PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다: ${reservation.id}")
        if (payment.status != ReservationPaymentStatus.APPROVED) {
            throw BusinessException(
                code = "PAYMENT_NOT_APPROVED",
                message = "결제가 승인된 예약만 확정할 수 있습니다: reservationId=${reservation.id}, paymentStatus=${payment.status}"
            )
        }
    }

    private fun shouldReleaseInventoryOnCancel(reservation: Reservation): Boolean {
        if (reservation.status == ReservationStatus.CONFIRMED) {
            return true
        }
        if (reservation.memberId == null) {
            return reservation.status == ReservationStatus.PENDING
        }

        val payment = reservationPaymentPort.findByReservationId(reservation.id)
            ?: return false
        return payment.status == ReservationPaymentStatus.APPROVED ||
            payment.status == ReservationPaymentStatus.CANCEL_REQUESTED ||
            payment.status == ReservationPaymentStatus.CANCEL_FAILED
    }

    @Transactional
    fun checkInReservation(propertyId: String, reservationId: String, roomId: String): Reservation {
        val reservation = getReservation(propertyId, reservationId)
        requireArrivalDateToday(reservation, "CHECK_IN_NOT_ALLOWED_DATE", "체크인 당일에만 체크인할 수 있습니다.")

        val room = roomRepository.findById(roomId)
            ?: throw NotFoundException("ROOM_NOT_FOUND", "객실을 찾을 수 없습니다: $roomId")
        check(room.status == RoomStatus.AVAILABLE) {
            "AVAILABLE 상태의 객실만 배정할 수 있습니다: ${room.status}"
        }

        roomRepository.save(room.checkIn())
        val checkedIn = reservation.checkIn(roomId)
        val saved = reservationRepository.save(checkedIn)

        log.info("체크인: reservationId={}, roomId={}", saved.id, roomId)
        return saved
    }

    @Transactional
    fun checkOutReservation(propertyId: String, reservationId: String): Reservation {
        val reservation = getReservation(propertyId, reservationId)
        val checkedOut = reservation.checkOut()
        val saved = reservationRepository.save(checkedOut)

        if (reservation.roomId != null) {
            val room = roomRepository.findById(reservation.roomId!!)
            if (room != null) {
                roomRepository.save(room.checkOut())
            }
        }

        eventPublisher.publishEvent(
            ReservationCheckedOut(
                reservationId = saved.id,
                propertyId = reservation.propertyId,
                guestId = reservation.guestId,
                totalAmount = reservation.pricing.totalAmount,
                stayNights = reservation.nightCount.toLong()
            )
        )

        log.info("체크아웃: reservationId={}", saved.id)
        return saved
    }

    fun noShowReservation(propertyId: String, reservationId: String): Reservation {
        val reservation = getReservation(propertyId, reservationId)
        requireArrivalDateToday(reservation, "NO_SHOW_NOT_ALLOWED_DATE", "체크인 당일에만 노쇼 처리할 수 있습니다.")
        return reservationRepository.save(reservation.noShow())
    }

    private fun requireArrivalDateToday(reservation: Reservation, code: String, message: String) {
        val today = LocalDate.now(clock)
        if (reservation.dateRange.checkIn != today) {
            throw BusinessException(
                code = code,
                message = "$message checkIn=${reservation.dateRange.checkIn}, today=$today"
            )
        }
    }
}
