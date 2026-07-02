package com.stayops.guest.infrastructure.persistence.rdb

import com.stayops.guest.domain.model.Guest
import com.stayops.guest.domain.model.GuestTier
import com.stayops.guest.domain.model.VisitSummary
import com.stayops.guest.domain.repository.GuestRepository
import com.stayops.jooq.generated.Tables.GUESTS
import com.stayops.jooq.generated.tables.records.GuestsRecord
import com.stayops.shared.config.RdbPersistence
import com.stayops.shared.domain.Money
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbGuestRepository(
    private val dsl: DSLContext
) : GuestRepository {

    override fun save(guest: Guest): Guest {
        dsl.insertInto(GUESTS)
            .set(GUESTS.ID, guest.id)
            .set(GUESTS.PROPERTY_ID, guest.propertyId)
            .set(GUESTS.NAME, guest.name)
            .set(GUESTS.PHONE, guest.phone)
            .set(GUESTS.EMAIL, guest.email)
            .set(GUESTS.TIER, guest.tier.name)
            .set(GUESTS.MEMO, guest.memo)
            .set(GUESTS.TOTAL_VISITS, guest.visitSummary.totalVisits)
            .set(GUESTS.TOTAL_SPEND_AMOUNT, guest.visitSummary.totalSpend.amount.toLong())
            .set(GUESTS.LAST_VISIT_DATE, guest.visitSummary.lastVisitDate)
            .set(GUESTS.AVERAGE_STAY_NIGHTS, guest.visitSummary.averageStayNights)
            .set(GUESTS.VERSION, guest.version)
            .set(GUESTS.CREATED_AT, guest.createdAt.toOffsetDateTime())
            .set(GUESTS.UPDATED_AT, guest.updatedAt.toOffsetDateTime())
            .onConflict(GUESTS.ID)
            .doUpdate()
            .set(GUESTS.PROPERTY_ID, guest.propertyId)
            .set(GUESTS.NAME, guest.name)
            .set(GUESTS.PHONE, guest.phone)
            .set(GUESTS.EMAIL, guest.email)
            .set(GUESTS.TIER, guest.tier.name)
            .set(GUESTS.MEMO, guest.memo)
            .set(GUESTS.TOTAL_VISITS, guest.visitSummary.totalVisits)
            .set(GUESTS.TOTAL_SPEND_AMOUNT, guest.visitSummary.totalSpend.amount.toLong())
            .set(GUESTS.LAST_VISIT_DATE, guest.visitSummary.lastVisitDate)
            .set(GUESTS.AVERAGE_STAY_NIGHTS, guest.visitSummary.averageStayNights)
            .set(GUESTS.VERSION, guest.version)
            .set(GUESTS.CREATED_AT, guest.createdAt.toOffsetDateTime())
            .set(GUESTS.UPDATED_AT, guest.updatedAt.toOffsetDateTime())
            .execute()

        return findById(guest.id) ?: guest
    }

    override fun findById(id: String): Guest? =
        dsl.selectFrom(GUESTS)
            .where(GUESTS.ID.eq(id))
            .fetchOne()
            ?.toDomain()

    override fun findByPropertyIdAndPhone(propertyId: String, phone: String): Guest? =
        dsl.selectFrom(GUESTS)
            .where(GUESTS.PROPERTY_ID.eq(propertyId))
            .and(GUESTS.PHONE.eq(phone))
            .fetchOne()
            ?.toDomain()

    override fun findByPropertyId(propertyId: String): List<Guest> =
        dsl.selectFrom(GUESTS)
            .where(GUESTS.PROPERTY_ID.eq(propertyId))
            .orderBy(GUESTS.ID.asc())
            .fetch { record -> record.toDomain() }

    override fun findByPropertyIdAndTier(propertyId: String, tier: GuestTier): List<Guest> =
        dsl.selectFrom(GUESTS)
            .where(GUESTS.PROPERTY_ID.eq(propertyId))
            .and(GUESTS.TIER.eq(tier.name))
            .orderBy(GUESTS.ID.asc())
            .fetch { record -> record.toDomain() }

    override fun findByPropertyIdAndNameContaining(propertyId: String, name: String): List<Guest> =
        dsl.selectFrom(GUESTS)
            .where(GUESTS.PROPERTY_ID.eq(propertyId))
            .and(GUESTS.NAME.contains(name))
            .orderBy(GUESTS.ID.asc())
            .fetch { record -> record.toDomain() }

    private fun GuestsRecord.toDomain(): Guest =
        Guest.reconstitute(
            id = get(GUESTS.ID),
            propertyId = get(GUESTS.PROPERTY_ID),
            name = get(GUESTS.NAME),
            phone = get(GUESTS.PHONE),
            email = get(GUESTS.EMAIL),
            tier = GuestTier.valueOf(get(GUESTS.TIER)),
            memo = get(GUESTS.MEMO),
            visitSummary = VisitSummary(
                totalVisits = get(GUESTS.TOTAL_VISITS),
                totalSpend = Money.of(get(GUESTS.TOTAL_SPEND_AMOUNT)),
                lastVisitDate = get(GUESTS.LAST_VISIT_DATE),
                averageStayNights = get(GUESTS.AVERAGE_STAY_NIGHTS)
            ),
            version = get(GUESTS.VERSION),
            createdAt = get(GUESTS.CREATED_AT).toInstant(),
            updatedAt = get(GUESTS.UPDATED_AT).toInstant()
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
