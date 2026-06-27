package com.stayops.reservation.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.RESERVATION_INTENTS
import com.stayops.jooq.generated.tables.records.ReservationIntentsRecord
import com.stayops.reservation.domain.model.GuestInfo
import com.stayops.reservation.domain.model.ReservationChannel
import com.stayops.reservation.domain.model.ReservationIntent
import com.stayops.reservation.domain.model.ReservationIntentStatus
import com.stayops.reservation.domain.model.ReservationPricing
import com.stayops.reservation.domain.repository.ReservationIntentRepository
import com.stayops.shared.config.RdbPersistence
import com.stayops.shared.domain.DateRange
import com.stayops.shared.domain.Money
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbReservationIntentRepository(
    private val dsl: DSLContext
) : ReservationIntentRepository {

    override fun save(intent: ReservationIntent): ReservationIntent {
        dsl.insertInto(RESERVATION_INTENTS)
            .set(RESERVATION_INTENTS.ID, intent.id)
            .set(RESERVATION_INTENTS.MEMBER_ID, intent.memberId)
            .set(RESERVATION_INTENTS.PROPERTY_ID, intent.propertyId)
            .set(RESERVATION_INTENTS.ROOM_TYPE_ID, intent.roomTypeId)
            .set(RESERVATION_INTENTS.GUEST_NAME, intent.guestInfo.name)
            .set(RESERVATION_INTENTS.GUEST_PHONE, intent.guestInfo.phone)
            .set(RESERVATION_INTENTS.GUEST_EMAIL, intent.guestInfo.email)
            .set(RESERVATION_INTENTS.CHECK_IN, intent.dateRange.checkIn)
            .set(RESERVATION_INTENTS.CHECK_OUT, intent.dateRange.checkOut)
            .set(RESERVATION_INTENTS.NIGHT_COUNT, intent.nightCount)
            .set(RESERVATION_INTENTS.NUMBER_OF_GUESTS, intent.numberOfGuests)
            .set(RESERVATION_INTENTS.CHANNEL_CODE, intent.channel.channelCode)
            .set(RESERVATION_INTENTS.EXTERNAL_RESERVATION_ID, intent.channel.externalReservationId)
            .set(RESERVATION_INTENTS.COMMISSION_RATE, intent.channel.commissionRate)
            .set(RESERVATION_INTENTS.ROOM_RATE_AMOUNT, intent.pricing.roomRate.amount)
            .set(RESERVATION_INTENTS.ROOM_RATE_CURRENCY, intent.pricing.roomRate.currency)
            .set(RESERVATION_INTENTS.ADDITIONAL_CHARGES_AMOUNT, intent.pricing.additionalCharges.amount)
            .set(RESERVATION_INTENTS.TOTAL_AMOUNT, intent.pricing.totalAmount.amount)
            .set(RESERVATION_INTENTS.COMMISSION_AMOUNT, intent.pricing.commissionAmount.amount)
            .set(RESERVATION_INTENTS.NET_AMOUNT, intent.pricing.netAmount.amount)
            .set(RESERVATION_INTENTS.PAYMENT_ID, intent.paymentId)
            .set(RESERVATION_INTENTS.HOLD_ID, intent.holdId)
            .set(RESERVATION_INTENTS.RESERVATION_ID, intent.reservationId)
            .set(RESERVATION_INTENTS.STATUS, intent.status.name)
            .set(RESERVATION_INTENTS.EXPIRES_AT, intent.expiresAt.toOffsetDateTime())
            .set(RESERVATION_INTENTS.VERSION, intent.version)
            .set(RESERVATION_INTENTS.CREATED_AT, intent.createdAt.toOffsetDateTime())
            .set(RESERVATION_INTENTS.UPDATED_AT, intent.updatedAt.toOffsetDateTime())
            .onConflict(RESERVATION_INTENTS.ID)
            .doUpdate()
            .set(RESERVATION_INTENTS.MEMBER_ID, intent.memberId)
            .set(RESERVATION_INTENTS.PROPERTY_ID, intent.propertyId)
            .set(RESERVATION_INTENTS.ROOM_TYPE_ID, intent.roomTypeId)
            .set(RESERVATION_INTENTS.GUEST_NAME, intent.guestInfo.name)
            .set(RESERVATION_INTENTS.GUEST_PHONE, intent.guestInfo.phone)
            .set(RESERVATION_INTENTS.GUEST_EMAIL, intent.guestInfo.email)
            .set(RESERVATION_INTENTS.CHECK_IN, intent.dateRange.checkIn)
            .set(RESERVATION_INTENTS.CHECK_OUT, intent.dateRange.checkOut)
            .set(RESERVATION_INTENTS.NIGHT_COUNT, intent.nightCount)
            .set(RESERVATION_INTENTS.NUMBER_OF_GUESTS, intent.numberOfGuests)
            .set(RESERVATION_INTENTS.CHANNEL_CODE, intent.channel.channelCode)
            .set(RESERVATION_INTENTS.EXTERNAL_RESERVATION_ID, intent.channel.externalReservationId)
            .set(RESERVATION_INTENTS.COMMISSION_RATE, intent.channel.commissionRate)
            .set(RESERVATION_INTENTS.ROOM_RATE_AMOUNT, intent.pricing.roomRate.amount)
            .set(RESERVATION_INTENTS.ROOM_RATE_CURRENCY, intent.pricing.roomRate.currency)
            .set(RESERVATION_INTENTS.ADDITIONAL_CHARGES_AMOUNT, intent.pricing.additionalCharges.amount)
            .set(RESERVATION_INTENTS.TOTAL_AMOUNT, intent.pricing.totalAmount.amount)
            .set(RESERVATION_INTENTS.COMMISSION_AMOUNT, intent.pricing.commissionAmount.amount)
            .set(RESERVATION_INTENTS.NET_AMOUNT, intent.pricing.netAmount.amount)
            .set(RESERVATION_INTENTS.PAYMENT_ID, intent.paymentId)
            .set(RESERVATION_INTENTS.HOLD_ID, intent.holdId)
            .set(RESERVATION_INTENTS.RESERVATION_ID, intent.reservationId)
            .set(RESERVATION_INTENTS.STATUS, intent.status.name)
            .set(RESERVATION_INTENTS.EXPIRES_AT, intent.expiresAt.toOffsetDateTime())
            .set(RESERVATION_INTENTS.VERSION, intent.version)
            .set(RESERVATION_INTENTS.CREATED_AT, intent.createdAt.toOffsetDateTime())
            .set(RESERVATION_INTENTS.UPDATED_AT, intent.updatedAt.toOffsetDateTime())
            .execute()

        return findById(intent.id) ?: intent
    }

    override fun findById(id: String): ReservationIntent? =
        dsl.selectFrom(RESERVATION_INTENTS)
            .where(RESERVATION_INTENTS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    override fun existsActiveByMemberIdAndRoomTypeIdAndCheckInAndCheckOut(
        memberId: String,
        roomTypeId: String,
        checkIn: LocalDate,
        checkOut: LocalDate,
        now: Instant
    ): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(RESERVATION_INTENTS)
                .where(RESERVATION_INTENTS.MEMBER_ID.eq(memberId))
                .and(RESERVATION_INTENTS.ROOM_TYPE_ID.eq(roomTypeId))
                .and(RESERVATION_INTENTS.CHECK_IN.eq(checkIn))
                .and(RESERVATION_INTENTS.CHECK_OUT.eq(checkOut))
                .and(RESERVATION_INTENTS.STATUS.`in`(activeStatuses.map { it.name }))
                .and(RESERVATION_INTENTS.EXPIRES_AT.ge(now.toOffsetDateTime()))
        )

    private fun ReservationIntentsRecord.toDomain(): ReservationIntent =
        ReservationIntent.reconstitute(
            id = get(RESERVATION_INTENTS.ID),
            memberId = get(RESERVATION_INTENTS.MEMBER_ID),
            propertyId = get(RESERVATION_INTENTS.PROPERTY_ID),
            roomTypeId = get(RESERVATION_INTENTS.ROOM_TYPE_ID),
            guestInfo = GuestInfo(
                name = get(RESERVATION_INTENTS.GUEST_NAME),
                phone = get(RESERVATION_INTENTS.GUEST_PHONE),
                email = get(RESERVATION_INTENTS.GUEST_EMAIL)
            ),
            dateRange = DateRange.of(get(RESERVATION_INTENTS.CHECK_IN), get(RESERVATION_INTENTS.CHECK_OUT)),
            nightCount = get(RESERVATION_INTENTS.NIGHT_COUNT),
            numberOfGuests = get(RESERVATION_INTENTS.NUMBER_OF_GUESTS),
            channel = ReservationChannel(
                channelCode = get(RESERVATION_INTENTS.CHANNEL_CODE),
                externalReservationId = get(RESERVATION_INTENTS.EXTERNAL_RESERVATION_ID),
                commissionRate = get(RESERVATION_INTENTS.COMMISSION_RATE)
            ),
            pricing = ReservationPricing(
                roomRate = Money.of(get(RESERVATION_INTENTS.ROOM_RATE_AMOUNT), get(RESERVATION_INTENTS.ROOM_RATE_CURRENCY)),
                additionalCharges = Money.of(
                    get(RESERVATION_INTENTS.ADDITIONAL_CHARGES_AMOUNT),
                    get(RESERVATION_INTENTS.ROOM_RATE_CURRENCY)
                ),
                totalAmount = Money.of(get(RESERVATION_INTENTS.TOTAL_AMOUNT), get(RESERVATION_INTENTS.ROOM_RATE_CURRENCY)),
                commissionAmount = Money.of(
                    get(RESERVATION_INTENTS.COMMISSION_AMOUNT),
                    get(RESERVATION_INTENTS.ROOM_RATE_CURRENCY)
                ),
                netAmount = Money.of(get(RESERVATION_INTENTS.NET_AMOUNT), get(RESERVATION_INTENTS.ROOM_RATE_CURRENCY))
            ),
            paymentId = get(RESERVATION_INTENTS.PAYMENT_ID),
            holdId = get(RESERVATION_INTENTS.HOLD_ID),
            reservationId = get(RESERVATION_INTENTS.RESERVATION_ID),
            status = ReservationIntentStatus.valueOf(get(RESERVATION_INTENTS.STATUS)),
            expiresAt = get(RESERVATION_INTENTS.EXPIRES_AT).toInstant(),
            version = get(RESERVATION_INTENTS.VERSION),
            createdAt = get(RESERVATION_INTENTS.CREATED_AT).toInstant(),
            updatedAt = get(RESERVATION_INTENTS.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)

    companion object {
        private val activeStatuses = listOf(
            ReservationIntentStatus.PAYMENT_WAITING,
            ReservationIntentStatus.CONFIRM_REQUESTED
        )
    }
}
