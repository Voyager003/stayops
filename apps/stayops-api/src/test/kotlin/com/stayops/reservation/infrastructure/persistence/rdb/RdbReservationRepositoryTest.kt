package com.stayops.reservation.infrastructure.persistence.rdb

import com.stayops.RdbTestcontainersConfiguration
import com.stayops.jooq.generated.Tables.GUESTS
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.Tables.PROPERTIES
import com.stayops.jooq.generated.Tables.RESERVATIONS
import com.stayops.jooq.generated.Tables.ROOM_TYPES
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import com.stayops.reservation.domain.model.DateType
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.time.StayopsTimeProperties
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@SpringJUnitConfig(classes = [RdbReservationRepositoryTest.TestApplication::class])
@ActiveProfiles("rdb")
class RdbReservationRepositoryTest @Autowired constructor(
    private val reservationRepository: ReservationRepository,
    private val dsl: DSLContext
) {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(RdbTestcontainersConfiguration::class, RdbReservationRepository::class)
    class TestApplication {
        @Bean
        fun stayopsTimeProperties(): StayopsTimeProperties = StayopsTimeProperties()
    }

    @BeforeEach
    fun setUp() {
        dsl.deleteFrom(RESERVATIONS).execute()
        dsl.deleteFrom(GUESTS).execute()
        dsl.deleteFrom(ROOM_TYPES).execute()
        dsl.deleteFrom(PROPERTIES).execute()
        dsl.deleteFrom(MEMBERS).execute()
    }

    @Nested
    inner class `save_및_findById` {

        @Test
        fun `저장 후 모든 필드가 보존된 Reservation을 조회한다`() {
            insertDependencies()
            val reservation = newReservation(
                status = ReservationStatus.CONFIRMED,
                channelCode = "BOOKING",
                externalReservationId = "ota-1"
            )

            reservationRepository.save(reservation)

            val found = reservationRepository.findById("res-1")
            assertThat(found).isNotNull
            assertThat(found!!.propertyId).isEqualTo("prop-1")
            assertThat(found.roomTypeId).isEqualTo("rt-1")
            assertThat(found.guestId).isEqualTo("guest-1")
            assertThat(found.guestInfo.name).isEqualTo("김고객")
            assertThat(found.dateRange.checkIn).isEqualTo(LocalDate.of(2026, 7, 1))
            assertThat(found.dateRange.checkOut).isEqualTo(LocalDate.of(2026, 7, 3))
            assertThat(found.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(found.channel.channelCode).isEqualTo("BOOKING")
            assertThat(found.channel.externalReservationId).isEqualTo("ota-1")
            assertThat(found.pricing.totalAmount).isEqualTo(Money.of(BigDecimal("220000.00")))
            assertThat(found.memberId).isEqualTo("member-1")
        }
    }

    @Nested
    inner class `기본 조회` {

        @Test
        fun `숙소와 상태로 Reservation 목록을 조회한다`() {
            insertDependencies()
            reservationRepository.save(newReservation(id = "res-1", status = ReservationStatus.CONFIRMED))
            reservationRepository.save(newReservation(id = "res-2", status = ReservationStatus.PENDING))

            val result = reservationRepository.findByPropertyIdAndStatus("prop-1", ReservationStatus.CONFIRMED)

            assertThat(result.map { it.id }).containsExactly("res-1")
        }

        @Test
        fun `숙소와 투숙일 겹침 조건으로 Reservation 목록을 조회한다`() {
            insertDependencies()
            reservationRepository.save(
                newReservation(
                    id = "res-1",
                    checkIn = LocalDate.of(2026, 7, 1),
                    checkOut = LocalDate.of(2026, 7, 3)
                )
            )
            reservationRepository.save(
                newReservation(
                    id = "res-2",
                    checkIn = LocalDate.of(2026, 8, 1),
                    checkOut = LocalDate.of(2026, 8, 3)
                )
            )

            val result = reservationRepository.findByPropertyIdAndDateRange(
                "prop-1",
                LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 7, 4)
            )

            assertThat(result.map { it.id }).containsExactly("res-1")
        }

        @Test
        fun `숙소와 고객으로 Reservation 목록을 조회한다`() {
            insertDependencies()
            insertGuest("guest-2", phone = "010-0000-0002")
            reservationRepository.save(newReservation(id = "res-1", guestId = "guest-1"))
            reservationRepository.save(newReservation(id = "res-2", guestId = "guest-2"))

            val result = reservationRepository.findByPropertyIdAndGuestId("prop-1", "guest-2")

            assertThat(result.map { it.id }).containsExactly("res-2")
        }

        @Test
        fun `숙소와 채널 예약번호로 Reservation을 조회한다`() {
            insertDependencies()
            reservationRepository.save(
                newReservation(
                    id = "res-1",
                    channelCode = "BOOKING",
                    externalReservationId = "ota-1"
                )
            )

            val found = reservationRepository.findByPropertyIdAndChannelCodeAndExternalReservationId(
                "prop-1",
                "BOOKING",
                "ota-1"
            )

            assertThat(found!!.id).isEqualTo("res-1")
        }
    }

    @Nested
    inner class `페이지_및_검색` {

        @Test
        fun `회원 예약 목록을 생성일 내림차순으로 페이지 조회한다`() {
            insertDependencies()
            reservationRepository.save(newReservation(id = "res-1", createdAt = Instant.parse("2026-07-01T00:00:00Z")))
            reservationRepository.save(newReservation(id = "res-2", createdAt = Instant.parse("2026-07-02T00:00:00Z")))

            val result = reservationRepository.findPageByMemberId("member-1", page = 0, size = 1)

            assertThat(result.totalElements).isEqualTo(2)
            assertThat(result.totalPages).isEqualTo(2)
            assertThat(result.content.map { it.id }).containsExactly("res-2")
        }

        @Test
        fun `검색 조건으로 Reservation 페이지를 조회한다`() {
            insertDependencies()
            reservationRepository.save(
                newReservation(
                    id = "res-1",
                    status = ReservationStatus.CONFIRMED,
                    channelCode = "BOOKING",
                    guestName = "김고객",
                    checkIn = LocalDate.of(2026, 7, 1)
                )
            )
            reservationRepository.save(
                newReservation(
                    id = "res-2",
                    status = ReservationStatus.PENDING,
                    channelCode = "DIRECT",
                    guestName = "박예약",
                    checkIn = LocalDate.of(2026, 8, 1)
                )
            )

            val result = reservationRepository.search(
                "prop-1",
                ReservationSearchCriteria(
                    statuses = listOf(ReservationStatus.CONFIRMED),
                    channelCodes = listOf("BOOKING"),
                    dateType = DateType.CHECK_IN,
                    startDate = LocalDate.of(2026, 7, 1),
                    endDate = LocalDate.of(2026, 7, 31),
                    guestName = "김"
                ),
                page = 0,
                size = 10
            )

            assertThat(result.totalElements).isEqualTo(1)
            assertThat(result.content.map { it.id }).containsExactly("res-1")
        }

        @Test
        fun `여러 숙소 기준으로 Reservation 페이지를 조회한다`() {
            insertDependencies()
            insertProperty("prop-2")
            insertRoomType("rt-2", "prop-2")
            insertGuest("guest-2", propertyId = "prop-2", phone = "010-0000-0002")
            reservationRepository.save(newReservation(id = "res-1", propertyId = "prop-1"))
            reservationRepository.save(
                newReservation(
                    id = "res-2",
                    propertyId = "prop-2",
                    roomTypeId = "rt-2",
                    guestId = "guest-2"
                )
            )

            val result = reservationRepository.searchByPropertyIds(
                listOf("prop-1", "prop-2"),
                ReservationSearchCriteria(),
                page = 0,
                size = 10
            )

            assertThat(result.totalElements).isEqualTo(2)
        }
    }

    @Nested
    inner class `집계_및_활성예약확인` {

        @Test
        fun `숙소와 생성일 기준 Reservation 수를 계산한다`() {
            insertDependencies()
            reservationRepository.save(newReservation(id = "res-1", createdAt = Instant.parse("2026-07-01T01:00:00Z")))
            reservationRepository.save(newReservation(id = "res-2", createdAt = Instant.parse("2026-07-02T01:00:00Z")))

            val count = reservationRepository.countByPropertyIdAndCreatedDate("prop-1", LocalDate.of(2026, 7, 1))

            assertThat(count).isEqualTo(1)
        }

        @Test
        fun `확정 예약 또는 만료되지 않은 대기 예약이 있으면 true를 반환한다`() {
            insertDependencies()
            reservationRepository.save(
                newReservation(
                    id = "res-1",
                    status = ReservationStatus.PENDING,
                    expiresAt = Instant.parse("2026-07-01T01:00:00Z")
                )
            )

            val exists = reservationRepository.existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
                memberId = "member-1",
                roomTypeId = "rt-1",
                checkIn = LocalDate.of(2026, 7, 1),
                checkOut = LocalDate.of(2026, 7, 3),
                now = Instant.parse("2026-07-01T00:00:00Z")
            )

            assertThat(exists).isTrue()
        }
    }

    private fun newReservation(
        id: String = "res-1",
        propertyId: String = "prop-1",
        roomTypeId: String = "rt-1",
        guestId: String = "guest-1",
        guestName: String = "김고객",
        checkIn: LocalDate = LocalDate.of(2026, 7, 1),
        checkOut: LocalDate = checkIn.plusDays(2),
        status: ReservationStatus = ReservationStatus.PENDING,
        channelCode: String = "DIRECT",
        externalReservationId: String? = null,
        memberId: String? = "member-1",
        expiresAt: Instant? = null,
        createdAt: Instant = Instant.parse("2026-07-01T00:00:00Z")
    ): Reservation {
        val channel = ReservationChannel(
            channelCode = channelCode,
            externalReservationId = externalReservationId,
            commissionRate = BigDecimal("0.10000")
        )
        val pricing = ReservationPricing.calculate(
            roomRate = Money.of(200_000),
            additionalCharges = Money.of(20_000),
            commissionRate = channel.commissionRate
        )

        return Reservation.reconstitute(
            id = id,
            propertyId = propertyId,
            roomTypeId = roomTypeId,
            roomId = null,
            guestId = guestId,
            guestInfo = GuestInfo(name = guestName, phone = "010-0000-0000", email = "guest@stayops.com"),
            dateRange = DateRange.of(checkIn, checkOut),
            nightCount = DateRange.of(checkIn, checkOut).nights().toInt(),
            numberOfGuests = 2,
            status = status,
            channel = channel,
            pricing = pricing,
            memberId = memberId,
            expiresAt = expiresAt,
            version = 0L,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    private fun insertDependencies() {
        insertMember("member-1")
        insertProperty("prop-1")
        insertRoomType("rt-1", "prop-1")
        insertGuest("guest-1")
    }

    private fun insertMember(memberId: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(MEMBERS)
            .set(MEMBERS.ID, memberId)
            .set(MEMBERS.EMAIL, "$memberId@stayops.com")
            .set(MEMBERS.PASSWORD_HASH, "hashed-password")
            .set(MEMBERS.NAME, memberId)
            .set(MEMBERS.ROLE, MemberRole.OWNER.name)
            .set(MEMBERS.STATUS, MemberStatus.ACTIVE.name)
            .set(MEMBERS.VERSION, 0L)
            .set(MEMBERS.CREATED_AT, now)
            .set(MEMBERS.UPDATED_AT, now)
            .execute()
    }

    private fun insertProperty(propertyId: String) {
        val ownerId = "owner-$propertyId"
        insertMember(ownerId)
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dsl.insertInto(PROPERTIES)
            .set(PROPERTIES.ID, propertyId)
            .set(PROPERTIES.OWNER_ID, ownerId)
            .set(PROPERTIES.NAME, "Stay Ops Hotel")
            .set(PROPERTIES.TYPE, "HOTEL")
            .set(PROPERTIES.ADDRESS_STREET, "1 Test Street")
            .set(PROPERTIES.ADDRESS_CITY, "Seoul")
            .set(PROPERTIES.ADDRESS_STATE, "Seoul")
            .set(PROPERTIES.ADDRESS_ZIP_CODE, "00000")
            .set(PROPERTIES.ADDRESS_COUNTRY, "KR")
            .set(PROPERTIES.CONTACT_PHONE, "010-0000-0000")
            .set(PROPERTIES.CONTACT_EMAIL, "property@stayops.com")
            .set(PROPERTIES.DESCRIPTION, "test property")
            .set(PROPERTIES.STATUS, "ACTIVE")
            .set(PROPERTIES.TIMEZONE, "Asia/Seoul")
            .set(PROPERTIES.CURRENCY, "KRW")
            .set(PROPERTIES.VERSION, 0L)
            .set(PROPERTIES.CREATED_AT, now)
            .set(PROPERTIES.UPDATED_AT, now)
            .execute()
    }

    private fun insertRoomType(roomTypeId: String, propertyId: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dsl.insertInto(ROOM_TYPES)
            .set(ROOM_TYPES.ID, roomTypeId)
            .set(ROOM_TYPES.PROPERTY_ID, propertyId)
            .set(ROOM_TYPES.NAME, "디럭스")
            .set(ROOM_TYPES.DESCRIPTION, "test room type")
            .set(ROOM_TYPES.MAX_OCCUPANCY, 2)
            .set(ROOM_TYPES.BASE_PRICE_AMOUNT, BigDecimal("150000.00"))
            .set(ROOM_TYPES.BASE_PRICE_CURRENCY, "KRW")
            .set(ROOM_TYPES.VERSION, 0L)
            .set(ROOM_TYPES.CREATED_AT, now)
            .set(ROOM_TYPES.UPDATED_AT, now)
            .execute()
    }

    private fun insertGuest(
        guestId: String,
        propertyId: String = "prop-1",
        phone: String = "010-0000-0000"
    ) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dsl.insertInto(GUESTS)
            .set(GUESTS.ID, guestId)
            .set(GUESTS.PROPERTY_ID, propertyId)
            .set(GUESTS.NAME, guestId)
            .set(GUESTS.PHONE, phone)
            .set(GUESTS.EMAIL, "$guestId@stayops.com")
            .set(GUESTS.TIER, "NEW")
            .set(GUESTS.MEMO, null as String?)
            .set(GUESTS.TOTAL_VISITS, 0)
            .set(GUESTS.TOTAL_SPEND_AMOUNT, 0L)
            .set(GUESTS.LAST_VISIT_DATE, null as LocalDate?)
            .set(GUESTS.AVERAGE_STAY_NIGHTS, 0.0)
            .set(GUESTS.VERSION, 0L)
            .set(GUESTS.CREATED_AT, now)
            .set(GUESTS.UPDATED_AT, now)
            .execute()
    }
}
