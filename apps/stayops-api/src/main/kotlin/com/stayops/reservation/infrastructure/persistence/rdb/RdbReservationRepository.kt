package com.stayops.reservation.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.RESERVATIONS
import com.stayops.jooq.generated.tables.records.ReservationsRecord
import com.stayops.reservation.domain.model.DateType
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.Reservation
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.model.ReservationSearchCriteria
import com.stayops.reservation.domain.model.ReservationStatus
import com.stayops.reservation.domain.repository.ReservationRepository
import com.stayops.shared.config.RdbPersistence
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import com.stayops.shared.domain.PagedResult
import com.stayops.shared.time.StayopsTimeProperties
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbReservationRepository(
    private val dsl: DSLContext,
    private val timeProperties: StayopsTimeProperties
) : ReservationRepository {

    override fun save(reservation: Reservation): Reservation {
        dsl.insertInto(RESERVATIONS)
            .set(RESERVATIONS.ID, reservation.id)
            .set(RESERVATIONS.PROPERTY_ID, reservation.propertyId)
            .set(RESERVATIONS.ROOM_TYPE_ID, reservation.roomTypeId)
            .set(RESERVATIONS.ROOM_ID, reservation.roomId)
            .set(RESERVATIONS.GUEST_ID, reservation.guestId)
            .set(RESERVATIONS.GUEST_NAME, reservation.guestInfo.name)
            .set(RESERVATIONS.GUEST_PHONE, reservation.guestInfo.phone)
            .set(RESERVATIONS.GUEST_EMAIL, reservation.guestInfo.email)
            .set(RESERVATIONS.CHECK_IN, reservation.dateRange.checkIn)
            .set(RESERVATIONS.CHECK_OUT, reservation.dateRange.checkOut)
            .set(RESERVATIONS.NIGHT_COUNT, reservation.nightCount)
            .set(RESERVATIONS.NUMBER_OF_GUESTS, reservation.numberOfGuests)
            .set(RESERVATIONS.STATUS, reservation.status.name)
            .set(RESERVATIONS.CHANNEL_CODE, reservation.channel.channelCode)
            .set(RESERVATIONS.EXTERNAL_RESERVATION_ID, reservation.channel.externalReservationId)
            .set(RESERVATIONS.COMMISSION_RATE, reservation.channel.commissionRate)
            .set(RESERVATIONS.ROOM_RATE_AMOUNT, reservation.pricing.roomRate.amount)
            .set(RESERVATIONS.ROOM_RATE_CURRENCY, reservation.pricing.roomRate.currency)
            .set(RESERVATIONS.ADDITIONAL_CHARGES_AMOUNT, reservation.pricing.additionalCharges.amount)
            .set(RESERVATIONS.TOTAL_AMOUNT, reservation.pricing.totalAmount.amount)
            .set(RESERVATIONS.COMMISSION_AMOUNT, reservation.pricing.commissionAmount.amount)
            .set(RESERVATIONS.NET_AMOUNT, reservation.pricing.netAmount.amount)
            .set(RESERVATIONS.MEMBER_ID, reservation.memberId)
            .set(RESERVATIONS.EXPIRES_AT, reservation.expiresAt?.toOffsetDateTime())
            .set(RESERVATIONS.VERSION, reservation.version)
            .set(RESERVATIONS.CREATED_AT, reservation.createdAt.toOffsetDateTime())
            .set(RESERVATIONS.UPDATED_AT, reservation.updatedAt.toOffsetDateTime())
            .onConflict(RESERVATIONS.ID)
            .doUpdate()
            .set(RESERVATIONS.PROPERTY_ID, reservation.propertyId)
            .set(RESERVATIONS.ROOM_TYPE_ID, reservation.roomTypeId)
            .set(RESERVATIONS.ROOM_ID, reservation.roomId)
            .set(RESERVATIONS.GUEST_ID, reservation.guestId)
            .set(RESERVATIONS.GUEST_NAME, reservation.guestInfo.name)
            .set(RESERVATIONS.GUEST_PHONE, reservation.guestInfo.phone)
            .set(RESERVATIONS.GUEST_EMAIL, reservation.guestInfo.email)
            .set(RESERVATIONS.CHECK_IN, reservation.dateRange.checkIn)
            .set(RESERVATIONS.CHECK_OUT, reservation.dateRange.checkOut)
            .set(RESERVATIONS.NIGHT_COUNT, reservation.nightCount)
            .set(RESERVATIONS.NUMBER_OF_GUESTS, reservation.numberOfGuests)
            .set(RESERVATIONS.STATUS, reservation.status.name)
            .set(RESERVATIONS.CHANNEL_CODE, reservation.channel.channelCode)
            .set(RESERVATIONS.EXTERNAL_RESERVATION_ID, reservation.channel.externalReservationId)
            .set(RESERVATIONS.COMMISSION_RATE, reservation.channel.commissionRate)
            .set(RESERVATIONS.ROOM_RATE_AMOUNT, reservation.pricing.roomRate.amount)
            .set(RESERVATIONS.ROOM_RATE_CURRENCY, reservation.pricing.roomRate.currency)
            .set(RESERVATIONS.ADDITIONAL_CHARGES_AMOUNT, reservation.pricing.additionalCharges.amount)
            .set(RESERVATIONS.TOTAL_AMOUNT, reservation.pricing.totalAmount.amount)
            .set(RESERVATIONS.COMMISSION_AMOUNT, reservation.pricing.commissionAmount.amount)
            .set(RESERVATIONS.NET_AMOUNT, reservation.pricing.netAmount.amount)
            .set(RESERVATIONS.MEMBER_ID, reservation.memberId)
            .set(RESERVATIONS.EXPIRES_AT, reservation.expiresAt?.toOffsetDateTime())
            .set(RESERVATIONS.VERSION, reservation.version)
            .set(RESERVATIONS.CREATED_AT, reservation.createdAt.toOffsetDateTime())
            .set(RESERVATIONS.UPDATED_AT, reservation.updatedAt.toOffsetDateTime())
            .execute()

        return findById(reservation.id) ?: reservation
    }

    override fun findById(id: String): Reservation? =
        dsl.selectFrom(RESERVATIONS)
            .where(RESERVATIONS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    override fun findByPropertyId(propertyId: String): List<Reservation> =
        findAll(RESERVATIONS.PROPERTY_ID.eq(propertyId))

    override fun findByPropertyIdAndStatus(propertyId: String, status: ReservationStatus): List<Reservation> =
        findAll(
            RESERVATIONS.PROPERTY_ID.eq(propertyId)
                .and(RESERVATIONS.STATUS.eq(status.name))
        )

    override fun findByPropertyIdAndDateRange(
        propertyId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Reservation> =
        findAll(
            RESERVATIONS.PROPERTY_ID.eq(propertyId)
                .and(RESERVATIONS.CHECK_IN.le(endDate))
                .and(RESERVATIONS.CHECK_OUT.ge(startDate))
        )

    override fun findByPropertyIdAndGuestId(propertyId: String, guestId: String): List<Reservation> =
        findAll(
            RESERVATIONS.PROPERTY_ID.eq(propertyId)
                .and(RESERVATIONS.GUEST_ID.eq(guestId))
        )

    override fun findByPropertyIdAndChannelCode(propertyId: String, channelCode: String): List<Reservation> =
        findAll(
            RESERVATIONS.PROPERTY_ID.eq(propertyId)
                .and(RESERVATIONS.CHANNEL_CODE.eq(channelCode))
        )

    override fun findByPropertyIdAndChannelCodeAndExternalReservationId(
        propertyId: String,
        channelCode: String,
        externalReservationId: String
    ): Reservation? =
        dsl.selectFrom(RESERVATIONS)
            .where(RESERVATIONS.PROPERTY_ID.eq(propertyId))
            .and(RESERVATIONS.CHANNEL_CODE.eq(channelCode))
            .and(RESERVATIONS.EXTERNAL_RESERVATION_ID.eq(externalReservationId))
            .fetchOne()
            ?.toDomain()

    override fun findByMemberId(memberId: String): List<Reservation> =
        findAll(RESERVATIONS.MEMBER_ID.eq(memberId))

    override fun findPageByMemberId(memberId: String, page: Int, size: Int): PagedResult<Reservation> =
        executePagedSearch(RESERVATIONS.MEMBER_ID.eq(memberId), page, size)

    override fun search(
        propertyId: String,
        criteria: ReservationSearchCriteria,
        page: Int,
        size: Int
    ): PagedResult<Reservation> =
        executePagedSearch(buildSearchCondition(RESERVATIONS.PROPERTY_ID.eq(propertyId), criteria), page, size)

    override fun searchByPropertyIds(
        propertyIds: List<String>,
        criteria: ReservationSearchCriteria,
        page: Int,
        size: Int
    ): PagedResult<Reservation> {
        if (propertyIds.isEmpty()) {
            return PagedResult(emptyList(), totalElements = 0, page = page, size = size, totalPages = 0)
        }

        return executePagedSearch(
            buildSearchCondition(RESERVATIONS.PROPERTY_ID.`in`(propertyIds), criteria),
            page,
            size
        )
    }

    override fun countByPropertyIdAndCreatedDate(propertyId: String, date: LocalDate): Int {
        val zone = timeProperties.defaultZone()
        val startOfDay = date.atStartOfDay(zone).toInstant().toOffsetDateTime()
        val startOfNextDay = date.plusDays(1).atStartOfDay(zone).toInstant().toOffsetDateTime()

        return dsl.fetchCount(
            RESERVATIONS,
            RESERVATIONS.PROPERTY_ID.eq(propertyId)
                .and(RESERVATIONS.CREATED_AT.ge(startOfDay))
                .and(RESERVATIONS.CREATED_AT.lt(startOfNextDay))
        )
    }

    override fun existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
        memberId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        now: Instant
    ): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(RESERVATIONS)
                .where(RESERVATIONS.MEMBER_ID.eq(memberId))
                .and(RESERVATIONS.ROOM_TYPE_ID.eq(roomTypeId))
                .and(RESERVATIONS.CHECK_IN.eq(checkIn))
                .and(RESERVATIONS.CHECK_OUT.eq(checkOut))
                .and(
                    RESERVATIONS.STATUS.eq(ReservationStatus.CONFIRMED.name)
                        .or(
                            RESERVATIONS.STATUS.eq(ReservationStatus.PENDING.name)
                                .and(
                                    RESERVATIONS.EXPIRES_AT.isNull
                                        .or(RESERVATIONS.EXPIRES_AT.ge(now.toOffsetDateTime()))
                                )
                        )
                )
        )

    private fun findAll(condition: Condition): List<Reservation> =
        dsl.selectFrom(RESERVATIONS)
            .where(condition)
            .orderBy(RESERVATIONS.CREATED_AT.desc(), RESERVATIONS.ID.asc())
            .fetch { record -> record.toDomain() }

    private fun executePagedSearch(condition: Condition, page: Int, size: Int): PagedResult<Reservation> {
        val totalElements = dsl.fetchCount(RESERVATIONS, condition).toLong()
        val content = dsl.selectFrom(RESERVATIONS)
            .where(condition)
            .orderBy(RESERVATIONS.CREATED_AT.desc(), RESERVATIONS.ID.asc())
            .limit(size)
            .offset(page * size)
            .fetch { record -> record.toDomain() }
        val totalPages = if (totalElements == 0L) 0 else ((totalElements - 1) / size + 1).toInt()

        return PagedResult(
            content = content,
            totalElements = totalElements,
            page = page,
            size = size,
            totalPages = totalPages
        )
    }

    private fun buildSearchCondition(
        baseCondition: Condition,
        criteria: ReservationSearchCriteria
    ): Condition {
        var condition = baseCondition

        criteria.statuses?.takeIf { it.isNotEmpty() }?.let { statuses ->
            condition = condition.and(RESERVATIONS.STATUS.`in`(statuses.map { it.name }))
        }

        criteria.roomTypeId?.let { roomTypeId ->
            condition = condition.and(RESERVATIONS.ROOM_TYPE_ID.eq(roomTypeId))
        }

        criteria.channelCodes?.takeIf { it.isNotEmpty() }?.let { channelCodes ->
            condition = condition.and(RESERVATIONS.CHANNEL_CODE.`in`(channelCodes))
        }

        if (criteria.startDate != null && criteria.endDate != null) {
            condition = condition.and(dateCondition(criteria))
        }

        criteria.guestName?.takeIf { it.isNotBlank() }?.let { guestName ->
            condition = condition.and(DSL.lower(RESERVATIONS.GUEST_NAME).like("${guestName.lowercase()}%"))
        }

        return condition
    }

    private fun dateCondition(criteria: ReservationSearchCriteria): Condition =
        when (criteria.dateType ?: DateType.CHECK_IN) {
            DateType.CHECK_IN ->
                RESERVATIONS.CHECK_IN.ge(criteria.startDate).and(RESERVATIONS.CHECK_IN.le(criteria.endDate))

            DateType.CHECK_OUT ->
                RESERVATIONS.CHECK_OUT.ge(criteria.startDate).and(RESERVATIONS.CHECK_OUT.le(criteria.endDate))

            DateType.CREATED -> {
                val zone = timeProperties.defaultZone()
                val startInstant = criteria.startDate!!.atStartOfDay(zone).toInstant().toOffsetDateTime()
                val endInstant = criteria.endDate!!.plusDays(1).atStartOfDay(zone).toInstant().toOffsetDateTime()
                RESERVATIONS.CREATED_AT.ge(startInstant).and(RESERVATIONS.CREATED_AT.lt(endInstant))
            }
        }

    private fun ReservationsRecord.toDomain(): Reservation =
        Reservation.reconstitute(
            id = get(RESERVATIONS.ID),
            propertyId = get(RESERVATIONS.PROPERTY_ID),
            roomTypeId = get(RESERVATIONS.ROOM_TYPE_ID),
            roomId = get(RESERVATIONS.ROOM_ID),
            guestId = get(RESERVATIONS.GUEST_ID),
            guestInfo = GuestInfo(
                name = get(RESERVATIONS.GUEST_NAME),
                phone = get(RESERVATIONS.GUEST_PHONE),
                email = get(RESERVATIONS.GUEST_EMAIL)
            ),
            dateRange = DateRange.of(get(RESERVATIONS.CHECK_IN), get(RESERVATIONS.CHECK_OUT)),
            nightCount = get(RESERVATIONS.NIGHT_COUNT),
            numberOfGuests = get(RESERVATIONS.NUMBER_OF_GUESTS),
            status = ReservationStatus.valueOf(get(RESERVATIONS.STATUS)),
            channel = ReservationChannel(
                channelCode = get(RESERVATIONS.CHANNEL_CODE),
                externalReservationId = get(RESERVATIONS.EXTERNAL_RESERVATION_ID),
                commissionRate = get(RESERVATIONS.COMMISSION_RATE)
            ),
            pricing = ReservationPricing(
                roomRate = Money.of(get(RESERVATIONS.ROOM_RATE_AMOUNT), get(RESERVATIONS.ROOM_RATE_CURRENCY)),
                additionalCharges = Money.of(
                    get(RESERVATIONS.ADDITIONAL_CHARGES_AMOUNT),
                    get(RESERVATIONS.ROOM_RATE_CURRENCY)
                ),
                totalAmount = Money.of(get(RESERVATIONS.TOTAL_AMOUNT), get(RESERVATIONS.ROOM_RATE_CURRENCY)),
                commissionAmount = Money.of(
                    get(RESERVATIONS.COMMISSION_AMOUNT),
                    get(RESERVATIONS.ROOM_RATE_CURRENCY)
                ),
                netAmount = Money.of(get(RESERVATIONS.NET_AMOUNT), get(RESERVATIONS.ROOM_RATE_CURRENCY))
            ),
            memberId = get(RESERVATIONS.MEMBER_ID),
            expiresAt = get(RESERVATIONS.EXPIRES_AT)?.toInstant(),
            version = get(RESERVATIONS.VERSION),
            createdAt = get(RESERVATIONS.CREATED_AT).toInstant(),
            updatedAt = get(RESERVATIONS.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
