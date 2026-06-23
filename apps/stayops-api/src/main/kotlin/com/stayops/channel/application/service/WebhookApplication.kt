package com.stayops.channel.application.service

import com.stayops.channel.domain.model.ChannelMapping
import com.stayops.channel.domain.model.MappingType
import com.stayops.channel.domain.model.ProcessedWebhookEvent
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.ProcessedWebhookEventRepository
import com.stayops.channel.application.required.WebhookSignatureVerifier
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.provided.InventoryReservationService
import com.stayops.reservation.application.required.ReservationPaymentService
import com.stayops.reservation.domain.event.ReservationCancelled
import com.stayops.reservation.domain.event.ReservationCreated
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class WebhookApplication(
    private val channelRepository: ChannelRepository,
    private val channelMappingRepository: ChannelMappingRepository,
    private val processedEventRepository: ProcessedWebhookEventRepository,
    private val signatureVerifier: WebhookSignatureVerifier,
    private val channelSyncApplication: ChannelSyncApplication,
    private val reservationRepository: ReservationRepository,
    private val reservationPaymentPort: ReservationPaymentService,
    private val inventoryReservationService: InventoryReservationService,
    private val guestRepository: GuestRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val roomTypeRepository: RoomTypeRepository,
    private val idGenerator: IdGenerator
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handleWebhook(
        propertyId: String,
        channelCode: String,
        signature: String,
        rawBody: String,
        eventId: String,
        eventType: String,
        payload: Map<String, Any>
    ) {
        val channel = channelRepository.findByPropertyIdAndCode(propertyId, channelCode)
            ?: throw BusinessException(code = "CHANNEL_NOT_FOUND", message = "채널을 찾을 수 없습니다: $channelCode")

        if (!signatureVerifier.verify(channelCode, rawBody, signature)) {
            throw BusinessException(code = "INVALID_SIGNATURE", message = "Webhook 서명이 유효하지 않습니다")
        }

        val saved = processedEventRepository.saveIfAbsent(
            ProcessedWebhookEvent(
                id = idGenerator.generate(),
                eventId = eventId,
                channelCode = channelCode,
                propertyId = propertyId
            )
        )
        if (!saved) {
            log.info("중복 이벤트 (동시 요청): eventId={}", eventId)
            return
        }

        val mapping = channelMappingRepository.findByPropertyIdAndChannelCode(propertyId, channelCode)

        when (eventType) {
            "BOOKING" -> handleBookingEvent(propertyId, channelCode, channel.commissionRate, mapping, payload)
            "CANCELLATION" -> handleCancellationEvent(propertyId, channelCode, payload)
            else -> {
                log.warn("알 수 없는 이벤트 타입: {}", eventType)
            }
        }

        log.info("Webhook 처리 완료: eventId={}", eventId)
    }

    private fun handleBookingEvent(
        propertyId: String,
        channelCode: String,
        commissionRate: BigDecimal,
        mapping: ChannelMapping?,
        payload: Map<String, Any>
    ) {
        val roomTypeCode = payload["roomTypeCode"]?.toString()
            ?: throw BusinessException("INVALID_WEBHOOK", "roomTypeCode는 필수입니다")
        val bookingId = payload["bookingId"]?.toString()
            ?: throw BusinessException("INVALID_WEBHOOK", "bookingId는 필수입니다")
        val checkInDate = payload["checkInDate"]?.toString()
            ?.let { LocalDate.parse(it) }
            ?: throw BusinessException("INVALID_WEBHOOK", "checkInDate는 필수입니다")
        val checkOutDate = payload["checkOutDate"]?.toString()
            ?.let { LocalDate.parse(it) }
            ?: throw BusinessException("INVALID_WEBHOOK", "checkOutDate는 필수입니다")
        val guestName = payload["guestName"]?.toString()
            ?: throw BusinessException("INVALID_WEBHOOK", "guestName은 필수입니다")

        // Resolve roomTypeId: use mapping if exists, otherwise roomTypeCode is the internal ID
        val roomTypeId = mapping?.findInternalId(roomTypeCode, MappingType.ROOM_TYPE) ?: roomTypeCode

        log.info(
            "OTA 예약 수신: channelCode={}, roomTypeCode={}, roomTypeId={}, bookingId={}",
            channelCode, roomTypeCode, roomTypeId, bookingId
        )

        // 1. Reserve inventory for each date
        val dateRange = DateRange.of(checkInDate, checkOutDate)
        dateRange.allDates().forEach { date ->
            inventoryReservationService.reserve(propertyId, roomTypeId, date)
        }

        // 2. Create or find guest (OTA guests have no phone — use placeholder)
        val guest = guestRepository.findByPropertyIdAndPhone(propertyId, "OTA-$bookingId")
            ?: guestRepository.save(
                Guest.create(
                    id = idGenerator.generate(),
                    propertyId = propertyId,
                    name = guestName,
                    phone = "OTA-$bookingId"
                )
            )

        // 3. Create reservation (PENDING) then confirm (OTA = pre-paid externally)
        val bookingChannel = ReservationChannel(
            channelCode = channelCode,
            externalReservationId = bookingId,
            commissionRate = commissionRate
        )
        val roomType = roomTypeRepository.findById(roomTypeId)
        val nightCount = dateRange.nights().toInt()
        val roomRate = if (roomType != null) roomType.basePrice.multiply(nightCount) else Money.ZERO
        val pricing = ReservationPricing.calculate(
            roomRate = roomRate,
            additionalCharges = Money.ZERO,
            commissionRate = commissionRate
        )
        val reservation = Reservation.create(
            id = idGenerator.generate(),
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            guestId = guest.id,
            guestInfo = GuestInfo(name = guestName, phone = "OTA-$bookingId", email = null),
            dateRange = dateRange,
            numberOfGuests = 1,
            channel = bookingChannel,
            pricing = pricing
        ).confirm()

        val saved = reservationRepository.save(reservation)

        // 4. Record OTA payment as already approved without creating a PG outbox
        reservationPaymentPort.createApprovedExternalPayment(
            reservationId = saved.id,
            memberId = "OTA-$bookingId",
            amount = pricing.totalAmount,
            paymentKey = "OTA-$channelCode-$bookingId",
            method = "OTA"
        )

        // 5. Publish event for ARI sync to other channels
        eventPublisher.publishEvent(
            ReservationCreated(
                reservationId = saved.id,
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                dateRange = dateRange,
                channelCode = channelCode
            )
        )

        log.info("OTA 예약 생성 완료: reservationId={}, bookingId={}", saved.id, bookingId)
    }

    private fun handleCancellationEvent(
        propertyId: String,
        channelCode: String,
        payload: Map<String, Any>
    ) {
        val bookingId = payload["bookingId"]?.toString()
            ?: throw BusinessException("INVALID_WEBHOOK", "bookingId는 필수입니다")

        val reservation = reservationRepository.findByPropertyIdAndChannelCodeAndExternalReservationId(
            propertyId = propertyId,
            channelCode = channelCode,
            externalReservationId = bookingId
        )

        if (reservation == null) {
            log.warn("OTA 취소 대상 예약 없음: propertyId={}, channelCode={}, bookingId={}", propertyId, channelCode, bookingId)
            return
        }

        val cancelled = reservation.cancel()
        val saved = reservationRepository.save(cancelled)

        reservation.dateRange.allDates().forEach { date ->
            inventoryReservationService.release(reservation.propertyId, reservation.roomTypeId, date)
        }

        eventPublisher.publishEvent(
            ReservationCancelled(
                reservationId = saved.id,
                propertyId = reservation.propertyId,
                roomTypeId = reservation.roomTypeId,
                dateRange = reservation.dateRange
            )
        )

        log.info("OTA 예약 취소 완료: reservationId={}, bookingId={}", saved.id, bookingId)
    }
}
