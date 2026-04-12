package com.stayops.channel.application.service

import com.stayops.channel.domain.model.MappingType
import com.stayops.channel.domain.model.ProcessedWebhookEvent
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.ProcessedWebhookEventRepository
import com.stayops.channel.domain.service.SignatureVerifier
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.reservation.domain.event.ReservationCreated
import com.stayops.reservation.domain.model.BookingChannel
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class WebhookApplication(
    private val channelRepository: ChannelRepository,
    private val channelMappingRepository: ChannelMappingRepository,
    private val processedEventRepository: ProcessedWebhookEventRepository,
    private val signatureVerifier: SignatureVerifier,
    private val channelSyncApplication: ChannelSyncApplication,
    private val reservationRepository: ReservationRepository,
    private val inventoryReservationPort: InventoryReservationPort,
    private val guestRepository: GuestRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val roomTypeRepository: com.stayops.room.domain.repository.RoomTypeRepository,
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
            "CANCELLATION" -> {
                // TODO(미구현): OTA 취소 이벤트 수신 시 externalReservationId로 예약을 조회해
                //   Reservation.cancel() + 재고 복원 + 도메인 이벤트 발행을 수행해야 한다.
                //   현재는 로그만 남겨 운영 환경에서 OTA 취소가 **무시된다**. 결과적으로
                //   고객이 OTA에서 취소해도 PMS 예약은 CONFIRMED 상태로 남아 "유령 예약"과
                //   잘못된 재고 점유가 발생한다.
                //   우선순위: High. 해결 전까지 OTA 채널 운영 시 수동 취소 필요.
                log.warn(
                    "OTA 취소 수신 — 미구현으로 무시됨: channelCode={}, bookingId={}",
                    channelCode, payload["bookingId"]
                )
            }
            else -> {
                log.warn("알 수 없는 이벤트 타입: {}", eventType)
            }
        }

        log.info("Webhook 처리 완료: eventId={}", eventId)
    }

    private fun handleBookingEvent(
        propertyId: String,
        channelCode: String,
        commissionRate: java.math.BigDecimal,
        mapping: com.stayops.channel.domain.model.ChannelMapping?,
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
            inventoryReservationPort.reserve(propertyId, roomTypeId, date)
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

        // 3. Create reservation (PENDING) then confirm (OTA = pre-paid, no payment needed)
        val bookingChannel = BookingChannel(
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

        // 4. Publish event for ARI sync to other channels
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
}
