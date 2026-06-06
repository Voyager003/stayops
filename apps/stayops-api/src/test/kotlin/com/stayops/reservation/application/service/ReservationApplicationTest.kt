package com.stayops.reservation.application.service

import com.stayops.channel.domain.model.Channel
import com.stayops.channel.domain.repository.ChannelRepository
import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.inventory.application.port.InventoryReservationPort
import com.stayops.rate.domain.model.RatePlan
import com.stayops.rate.domain.model.RatePlanStatus
import com.stayops.rate.domain.repository.RatePlanRepository
import com.stayops.rate.domain.service.RateResolverService
import com.stayops.reservation.domain.model.DateType
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.PagedResult
import com.stayops.room.domain.model.Room
import com.stayops.room.domain.model.RoomType
import com.stayops.room.domain.repository.RoomRepository
import com.stayops.room.domain.repository.RoomTypeRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.IdGenerator
import com.stayops.shared.domain.Money
import com.stayops.shared.exception.BusinessException
import com.stayops.shared.exception.ConflictException
import com.stayops.shared.exception.NotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReservationApplicationTest : BehaviorSpec({

    val reservationRepository = mockk<ReservationRepository>()
    val roomTypeRepository = mockk<RoomTypeRepository>()
    val channelRepository = mockk<ChannelRepository>()
    val ratePlanRepository = mockk<RatePlanRepository>()
    val guestRepository = mockk<GuestRepository>()
    val inventoryReservationPort = mockk<InventoryReservationPort>()
    val roomRepository = mockk<RoomRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val rateResolverService = RateResolverService()
    val fixedClock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("Asia/Seoul"))
    val idGenerator = object : IdGenerator {
        override fun generate(): String = "test-id"
    }

    val sut = ReservationApplication(
        reservationRepository = reservationRepository,
        roomTypeRepository = roomTypeRepository,
        channelRepository = channelRepository,
        ratePlanRepository = ratePlanRepository,
        guestRepository = guestRepository,
        inventoryReservationPort = inventoryReservationPort,
        roomRepository = roomRepository,
        eventPublisher = eventPublisher,
        rateResolverService = rateResolverService,
        idGenerator = idGenerator,
        clock = fixedClock
    )

    val checkIn = LocalDate.of(2026, 4, 1)
    val checkOut = LocalDate.of(2026, 4, 3)

    fun sampleRoomType() = RoomType.create(
        id = "rt-1",
        propertyId = "prop-1",
        name = "디럭스 더블",
        description = "넓은 객실",
        maxOccupancy = 3,
        basePrice = Money.won(100_000)
    )

    fun directChannel() = Channel.createDirect(id = "ch-0", propertyId = "prop-1")

    fun otaChannel() = Channel.createOta(
        id = "ch-1",
        propertyId = "prop-1",
        code = "AGODA",
        name = "Agoda",
        commissionRate = BigDecimal("0.15"),
        apiEndpoint = "https://mock-ota/ari"
    )

    fun sampleGuest() = Guest.create(
        id = "guest-1",
        propertyId = "prop-1",
        name = "홍길동",
        phone = "010-1234-5678",
        email = "hong@test.com"
    )

    // -- 예약 생성: 정상 --

    given("예약 생성 시") {
        `when`("유효한 DIRECT 예약이면") {
            then("PENDING 상태의 예약이 생성된다") {
                clearAllMocks()
                every { roomTypeRepository.findById("rt-1") } returns sampleRoomType()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "DIRECT") } returns directChannel()
                every { ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus("prop-1", "rt-1", RatePlanStatus.ACTIVE) } returns emptyList()
                justRun { inventoryReservationPort.reserve("prop-1", "rt-1", any()) }
                every { guestRepository.findById("guest-1") } returns sampleGuest()
                every { reservationRepository.save(any()) } answers { firstArg() }
                justRun { eventPublisher.publishEvent(any()) }

                val result = sut.createReservation(
                    propertyId = "prop-1",
                    roomTypeId = "rt-1",
                    checkIn = checkIn,
                    checkOut = checkOut,
                    numberOfGuests = 2,
                    guestId = "guest-1",
                    guestName = "홍길동",
                    guestPhone = "010-1234-5678",
                    guestEmail = "hong@test.com",
                    channelCode = "DIRECT"
                )

                result.status shouldBe ReservationStatus.PENDING
                result.channel.commissionRate shouldBe BigDecimal.ZERO
                result.nightCount shouldBe 2
                result.pricing.totalAmount shouldBe Money.won(200_000)
                verify(exactly = 2) { inventoryReservationPort.reserve("prop-1", "rt-1", any()) }
            }
        }

        `when`("OTA 채널 예약이면") {
            then("수수료가 적용된다") {
                clearAllMocks()
                every { roomTypeRepository.findById("rt-1") } returns sampleRoomType()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "AGODA") } returns otaChannel()
                every { ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus("prop-1", "rt-1", RatePlanStatus.ACTIVE) } returns emptyList()
                justRun { inventoryReservationPort.reserve("prop-1", "rt-1", any()) }
                every { guestRepository.findById("guest-1") } returns sampleGuest()
                every { reservationRepository.save(any()) } answers { firstArg() }
                justRun { eventPublisher.publishEvent(any()) }

                val result = sut.createReservation(
                    propertyId = "prop-1",
                    roomTypeId = "rt-1",
                    checkIn = checkIn,
                    checkOut = checkOut,
                    numberOfGuests = 2,
                    guestId = "guest-1",
                    guestName = "홍길동",
                    guestPhone = "010-1234-5678",
                    guestEmail = null,
                    channelCode = "AGODA"
                )

                result.channel.commissionRate shouldBe BigDecimal("0.15")
                result.pricing.commissionAmount shouldBe Money.won(30_000)
                result.pricing.netAmount shouldBe Money.won(170_000)
            }
        }
    }

    // -- 예약 확정 --

    given("예약 확정 시") {
        `when`("PENDING 상태의 예약을 확정하면") {
            then("CONFIRMED 상태로 변경된다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-c1", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                )
                every { reservationRepository.findById("rsv-c1") } returns reservation
                every { reservationRepository.save(any()) } answers { firstArg() }

                val result = sut.confirmReservation("prop-1", "rsv-c1")
                result.status shouldBe ReservationStatus.CONFIRMED
            }
        }
    }

    // -- 예약 취소 --

    given("예약 취소 시") {
        `when`("CONFIRMED 상태의 예약을 취소하면") {
            then("CANCELLED 상태로 변경되고 재고가 복원된다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-c2", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                ).confirm()
                every { reservationRepository.findById("rsv-c2") } returns reservation
                every { reservationRepository.save(any()) } answers { firstArg() }
                justRun { inventoryReservationPort.release("prop-1", "rt-1", any()) }
                justRun { eventPublisher.publishEvent(any()) }

                val result = sut.cancelReservation("prop-1", "rsv-c2")

                result.status shouldBe ReservationStatus.CANCELLED
                verify(exactly = 2) { inventoryReservationPort.release("prop-1", "rt-1", any()) }
            }
        }

        `when`("CHECKED_IN 상태의 예약을 취소하면") {
            then("예외가 발생한다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-c3", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                ).confirm().checkIn("room-101")
                every { reservationRepository.findById("rsv-c3") } returns reservation

                shouldThrow<IllegalStateException> {
                    sut.cancelReservation("prop-1", "rsv-c3")
                }
            }
        }
    }

    // -- 노쇼 --

    given("노쇼 처리 시") {
        `when`("CONFIRMED 상태의 예약을 노쇼 처리하면") {
            then("NO_SHOW 상태로 변경된다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-c4", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                ).confirm()
                every { reservationRepository.findById("rsv-c4") } returns reservation
                every { reservationRepository.save(any()) } answers { firstArg() }

                val result = sut.noShowReservation("prop-1", "rsv-c4")
                result.status shouldBe ReservationStatus.NO_SHOW
            }
        }

        `when`("체크인 날짜가 오늘이 아닌 예약을 노쇼 처리하면") {
            then("예외가 발생하고 저장하지 않는다") {
                clearAllMocks()
                val futureReservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-noshow-future", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn.plusDays(1), checkOut.plusDays(1)), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                ).confirm()
                every { reservationRepository.findById("rsv-noshow-future") } returns futureReservation

                val exception = shouldThrow<BusinessException> {
                    sut.noShowReservation("prop-1", "rsv-noshow-future")
                }

                exception.code shouldBe "NO_SHOW_NOT_ALLOWED_DATE"
                verify(exactly = 0) { reservationRepository.save(any()) }
            }
        }
    }

    // -- 예약 생성: 실패 --

    given("예약 생성 실패 시") {
        `when`("존재하지 않는 RoomType이면") {
            then("NotFoundException이 발생한다") {
                clearAllMocks()
                every { roomTypeRepository.findById("rt-999") } returns null

                shouldThrow<NotFoundException> {
                    sut.createReservation(
                        propertyId = "prop-1", roomTypeId = "rt-999",
                        checkIn = checkIn, checkOut = checkOut,
                        numberOfGuests = 2, guestId = "guest-1",
                        guestName = "홍길동", guestPhone = "010-1234-5678",
                        guestEmail = null, channelCode = "DIRECT"
                    )
                }
            }
        }

        `when`("재고 충돌이 발생하면") {
            then("ConflictException이 전파된다") {
                clearAllMocks()
                every { roomTypeRepository.findById("rt-1") } returns sampleRoomType()
                every { channelRepository.findByPropertyIdAndCode("prop-1", "DIRECT") } returns directChannel()
                every { ratePlanRepository.findByPropertyIdAndRoomTypeIdAndStatus(any(), any(), any()) } returns emptyList()
                every { inventoryReservationPort.reserve("prop-1", "rt-1", any()) } throws
                        ConflictException("INVENTORY_CONFLICT", "재고 변경 충돌")

                shouldThrow<ConflictException> {
                    sut.createReservation(
                        propertyId = "prop-1", roomTypeId = "rt-1",
                        checkIn = checkIn, checkOut = checkOut,
                        numberOfGuests = 2, guestId = "guest-1",
                        guestName = "홍길동", guestPhone = "010-1234-5678",
                        guestEmail = null, channelCode = "DIRECT"
                    )
                }
            }
        }
    }

    // -- 체크인 --

    given("체크인 시") {
        `when`("CONFIRMED 예약에 AVAILABLE 객실을 배정하면") {
            then("CHECKED_IN 상태로 변경되고 Room이 OCCUPIED로 전환된다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-ci1", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                ).confirm()
                val room = Room.create(id = "room-101", propertyId = "prop-1", roomTypeId = "rt-1", roomNumber = "101", floor = 1)

                every { reservationRepository.findById("rsv-ci1") } returns reservation
                every { roomRepository.findById("room-101") } returns room
                every { roomRepository.save(any()) } answers { firstArg() }
                every { reservationRepository.save(any()) } answers { firstArg() }

                val result = sut.checkInReservation("prop-1", "rsv-ci1", "room-101")

                result.status shouldBe ReservationStatus.CHECKED_IN
                result.roomId shouldBe "room-101"
                verify { roomRepository.save(match { it.status == com.stayops.room.domain.model.RoomStatus.OCCUPIED }) }
            }
        }

        `when`("체크인 날짜가 오늘이 아닌 예약에 객실을 배정하면") {
            then("예외가 발생하고 객실 상태를 변경하지 않는다") {
                clearAllMocks()
                val futureReservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-ci-future", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn.plusDays(1), checkOut.plusDays(1)), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                ).confirm()

                every { reservationRepository.findById("rsv-ci-future") } returns futureReservation

                val exception = shouldThrow<BusinessException> {
                    sut.checkInReservation("prop-1", "rsv-ci-future", "room-101")
                }

                exception.code shouldBe "CHECK_IN_NOT_ALLOWED_DATE"
                verify(exactly = 0) { roomRepository.findById(any()) }
                verify(exactly = 0) { roomRepository.save(any()) }
                verify(exactly = 0) { reservationRepository.save(any()) }
            }
        }
    }

    // -- 검색 --

    given("예약 검색 시") {
        `when`("상태와 채널 필터를 적용하면") {
            then("조건에 맞는 페이징 결과를 반환한다") {
                clearAllMocks()
                val criteria = ReservationSearchCriteria(
                    statuses = listOf(ReservationStatus.CONFIRMED),
                    channelCodes = listOf("AGODA"),
                    dateType = DateType.CHECK_IN,
                    startDate = checkIn,
                    endDate = checkOut
                )
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-s1", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("AGODA", null, BigDecimal("0.15")),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal("0.15"))
                ).confirm()

                every {
                    reservationRepository.search("prop-1", criteria, 0, 20)
                } returns PagedResult(
                    content = listOf(reservation),
                    totalElements = 1,
                    page = 0,
                    size = 20,
                    totalPages = 1
                )

                val result = sut.searchReservations("prop-1", criteria, 0, 20)

                result.totalElements shouldBe 1
                result.content[0].channel.channelCode shouldBe "AGODA"
                result.content[0].status shouldBe ReservationStatus.CONFIRMED
            }
        }
    }

    // -- 체크아웃 --

    given("체크아웃 시") {
        `when`("CHECKED_IN 예약을 체크아웃하면") {
            then("CHECKED_OUT 상태로 변경되고 Room이 CLEANING으로 전환된다") {
                clearAllMocks()
                val reservation = com.stayops.reservation.domain.model.Reservation.create(
                    id = "rsv-co1", propertyId = "prop-1", roomTypeId = "rt-1",
                    guestId = "guest-1",
                    guestInfo = com.stayops.reservation.domain.model.GuestInfo("홍길동", "010-1234-5678", null),
                    dateRange = DateRange.of(checkIn, checkOut), numberOfGuests = 2,
                    channel = com.stayops.reservation.domain.model.ReservationChannel("DIRECT", null, BigDecimal.ZERO),
                    pricing = com.stayops.reservation.domain.model.ReservationPricing.calculate(Money.won(200_000), Money.ZERO, BigDecimal.ZERO)
                ).confirm().checkIn("room-101")
                val room = Room.create(id = "room-101", propertyId = "prop-1", roomTypeId = "rt-1", roomNumber = "101", floor = 1).checkIn()

                every { reservationRepository.findById("rsv-co1") } returns reservation
                every { reservationRepository.save(any()) } answers { firstArg() }
                every { roomRepository.findById("room-101") } returns room
                every { roomRepository.save(any()) } answers { firstArg() }
                justRun { eventPublisher.publishEvent(any()) }

                val result = sut.checkOutReservation("prop-1", "rsv-co1")

                result.status shouldBe ReservationStatus.CHECKED_OUT
                verify { roomRepository.save(match { it.status == com.stayops.room.domain.model.RoomStatus.CLEANING }) }
            }
        }
    }
})
