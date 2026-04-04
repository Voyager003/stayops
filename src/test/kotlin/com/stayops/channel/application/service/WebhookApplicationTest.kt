package com.stayops.channel.application.service

import com.stayops.channel.domain.model.*
import com.stayops.channel.domain.repository.ChannelMappingRepository
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.channel.domain.repository.ProcessedWebhookEventRepository
import com.stayops.channel.domain.service.SignatureVerifier
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.service.RoomInventoryApplication
import com.stayops.reservation.domain.event.ReservationCreated
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.exception.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import java.math.BigDecimal
import java.time.LocalDate

class WebhookApplicationTest : BehaviorSpec({

    val channelRepository = mockk<ChannelRepository>()
    val mappingRepository = mockk<ChannelMappingRepository>()
    val processedEventRepository = mockk<ProcessedWebhookEventRepository>()
    val signatureVerifier = mockk<SignatureVerifier>()
    val channelSyncApplication = mockk<ChannelSyncApplication>()
    val reservationRepository = mockk<ReservationRepository>()
    val roomInventoryApplication = mockk<RoomInventoryApplication>()
    val guestRepository = mockk<GuestRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>()
    val roomTypeRepository = mockk<com.stayops.room.domain.repository.RoomTypeRepository>(relaxed = true)

    val sut = WebhookApplication(
        channelRepository = channelRepository,
        channelMappingRepository = mappingRepository,
        processedEventRepository = processedEventRepository,
        signatureVerifier = signatureVerifier,
        channelSyncApplication = channelSyncApplication,
        reservationRepository = reservationRepository,
        roomInventoryApplication = roomInventoryApplication,
        guestRepository = guestRepository,
        eventPublisher = eventPublisher,
        roomTypeRepository = roomTypeRepository
    )

    val otaChannel = Channel.createOta(
        id = "ch-1",
        propertyId = "prop-1",
        code = "AGODA",
        name = "Agoda",
        commissionRate = BigDecimal("0.15"),
        apiEndpoint = "https://mock-ota/ari"
    )

    given("BOOKING 이벤트 수신 시") {
        `when`("서명 검증 통과하고 유효한 payload이면") {
            then("예약이 CONFIRMED 상태로 생성되고 재고가 차감된다") {
                clearAllMocks()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel
                every { signatureVerifier.verify("AGODA", any(), "sha256=valid") } returns true
                every { processedEventRepository.save(any()) } answers { firstArg() }
                every { mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA") } returns null
                every { roomInventoryApplication.reserve("prop-1", "rt-deluxe", any()) } returns mockk()
                every { guestRepository.findByPropertyIdAndPhone("prop-1", "OTA-book-1") } returns null
                every { guestRepository.save(any()) } answers {
                    val g = firstArg<Guest>()
                    g
                }
                val savedSlot = slot<Reservation>()
                every { reservationRepository.save(capture(savedSlot)) } answers { firstArg() }
                every { eventPublisher.publishEvent(any<ReservationCreated>()) } just Runs

                sut.handleWebhook(
                    propertyId = "prop-1",
                    channelCode = "AGODA",
                    signature = "sha256=valid",
                    rawBody = """{"eventId":"evt-1"}""",
                    eventId = "evt-1",
                    eventType = "BOOKING",
                    payload = mapOf(
                        "roomTypeCode" to "rt-deluxe",
                        "bookingId" to "book-1",
                        "checkInDate" to "2026-05-01",
                        "checkOutDate" to "2026-05-03",
                        "guestName" to "John Doe"
                    )
                )

                // Verify inventory reserved for each night (2 nights: May 1, May 2)
                verify(exactly = 1) {
                    roomInventoryApplication.reserve("prop-1", "rt-deluxe", LocalDate.of(2026, 5, 1))
                }
                verify(exactly = 1) {
                    roomInventoryApplication.reserve("prop-1", "rt-deluxe", LocalDate.of(2026, 5, 2))
                }

                // Verify reservation saved as CONFIRMED
                savedSlot.captured.status shouldBe ReservationStatus.CONFIRMED
                savedSlot.captured.propertyId shouldBe "prop-1"
                savedSlot.captured.roomTypeId shouldBe "rt-deluxe"
                savedSlot.captured.channel.channelCode shouldBe "AGODA"
                savedSlot.captured.channel.externalReservationId shouldBe "book-1"
                savedSlot.captured.channel.commissionRate shouldBe BigDecimal("0.15")
                savedSlot.captured.guestInfo.name shouldBe "John Doe"
                savedSlot.captured.dateRange.checkIn shouldBe LocalDate.of(2026, 5, 1)
                savedSlot.captured.dateRange.checkOut shouldBe LocalDate.of(2026, 5, 3)

                // Verify event published
                verify(exactly = 1) {
                    eventPublisher.publishEvent(match<ReservationCreated> {
                        it.propertyId == "prop-1" &&
                            it.roomTypeId == "rt-deluxe" &&
                            it.channelCode == "AGODA"
                    })
                }
            }
        }

        `when`("매핑이 존재하면 roomTypeCode를 내부 ID로 변환한다") {
            then("매핑된 roomTypeId로 예약이 생성된다") {
                clearAllMocks()
                val mapping = ChannelMapping.create("map-1", "prop-1", "AGODA")
                    .addMapping(MappingEntry(internalId = "internal-rt-1", externalCode = "AGD-DLX", type = MappingType.ROOM_TYPE))

                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel
                every { signatureVerifier.verify("AGODA", any(), "sha256=valid") } returns true
                every { processedEventRepository.save(any()) } answers { firstArg() }
                every { mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA") } returns mapping
                every { roomInventoryApplication.reserve("prop-1", "internal-rt-1", any()) } returns mockk()
                every { guestRepository.findByPropertyIdAndPhone("prop-1", "OTA-book-2") } returns null
                every { guestRepository.save(any()) } answers { firstArg() }
                val savedSlot = slot<Reservation>()
                every { reservationRepository.save(capture(savedSlot)) } answers { firstArg() }
                every { eventPublisher.publishEvent(any<ReservationCreated>()) } just Runs

                sut.handleWebhook(
                    propertyId = "prop-1",
                    channelCode = "AGODA",
                    signature = "sha256=valid",
                    rawBody = """{"eventId":"evt-2"}""",
                    eventId = "evt-2",
                    eventType = "BOOKING",
                    payload = mapOf(
                        "roomTypeCode" to "AGD-DLX",
                        "bookingId" to "book-2",
                        "checkInDate" to "2026-06-10",
                        "checkOutDate" to "2026-06-12",
                        "guestName" to "Jane Smith"
                    )
                )

                savedSlot.captured.roomTypeId shouldBe "internal-rt-1"
            }
        }

        `when`("필수 필드가 누락되면") {
            then("BusinessException이 발생한다") {
                clearAllMocks()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel
                every { signatureVerifier.verify("AGODA", any(), "sha256=valid") } returns true
                every { processedEventRepository.save(any()) } answers { firstArg() }
                every { mappingRepository.findByPropertyIdAndChannelCode("prop-1", "AGODA") } returns null

                val exception = shouldThrow<BusinessException> {
                    sut.handleWebhook(
                        propertyId = "prop-1",
                        channelCode = "AGODA",
                        signature = "sha256=valid",
                        rawBody = """{"eventId":"evt-3"}""",
                        eventId = "evt-3",
                        eventType = "BOOKING",
                        payload = mapOf("bookingId" to "book-3")
                    )
                }
                exception.code shouldBe "INVALID_WEBHOOK"
            }
        }
    }

    given("중복 이벤트 수신 시") {
        `when`("save에서 DuplicateKeyException이 발생하면") {
            then("이벤트 처리를 건너뛴다") {
                clearAllMocks()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel
                every { signatureVerifier.verify(any(), any(), any()) } returns true
                every { processedEventRepository.save(any()) } throws DuplicateKeyException("duplicate eventId")

                sut.handleWebhook(
                    propertyId = "prop-1",
                    channelCode = "AGODA",
                    signature = "sha256=valid",
                    rawBody = "body",
                    eventId = "evt-dup",
                    eventType = "BOOKING",
                    payload = emptyMap()
                )

                verify(exactly = 0) { mappingRepository.findByPropertyIdAndChannelCode(any(), any()) }
                verify(exactly = 0) { reservationRepository.save(any()) }
            }
        }
    }

    given("서명이 유효하지 않을 때") {
        `when`("verify가 false를 반환하면") {
            then("BusinessException이 발생한다") {
                clearAllMocks()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel
                every { signatureVerifier.verify(any(), any(), any()) } returns false

                val exception = shouldThrow<BusinessException> {
                    sut.handleWebhook(
                        propertyId = "prop-1",
                        channelCode = "AGODA",
                        signature = "sha256=invalid",
                        rawBody = "body",
                        eventId = "evt-2",
                        eventType = "BOOKING",
                        payload = emptyMap()
                    )
                }
                exception.code shouldBe "INVALID_SIGNATURE"
            }
        }
    }
})
