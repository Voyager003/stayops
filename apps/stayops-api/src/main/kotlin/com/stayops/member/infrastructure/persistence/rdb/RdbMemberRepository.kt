package com.stayops.member.infrastructure.persistence.rdb

import com.stayops.jooq.generated.Tables.MEMBER_PROPERTY_ACCESSES
import com.stayops.jooq.generated.Tables.MEMBERS
import com.stayops.jooq.generated.tables.records.MembersRecord
import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import com.stayops.member.domain.model.PropertyAccess
import com.stayops.member.domain.model.PropertyRole
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.shared.config.RdbPersistence
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RdbPersistence
@Repository
class RdbMemberRepository(
    private val dsl: DSLContext
) : MemberRepository {

    override fun save(member: Member): Member =
        dsl.transactionResult { configuration ->
            val tx = DSL.using(configuration)

            tx.insertInto(MEMBERS)
                .set(MEMBERS.ID, member.id)
                .set(MEMBERS.EMAIL, member.email)
                .set(MEMBERS.PASSWORD_HASH, member.passwordHash)
                .set(MEMBERS.NAME, member.name)
                .set(MEMBERS.ROLE, member.role.name)
                .set(MEMBERS.STATUS, member.status.name)
                .set(MEMBERS.LAST_LOGIN_AT, member.lastLoginAt?.toOffsetDateTime())
                .set(MEMBERS.VERSION, member.version)
                .set(MEMBERS.CREATED_AT, member.createdAt.toOffsetDateTime())
                .set(MEMBERS.UPDATED_AT, member.updatedAt.toOffsetDateTime())
                .onConflict(MEMBERS.ID)
                .doUpdate()
                .set(MEMBERS.EMAIL, member.email)
                .set(MEMBERS.PASSWORD_HASH, member.passwordHash)
                .set(MEMBERS.NAME, member.name)
                .set(MEMBERS.ROLE, member.role.name)
                .set(MEMBERS.STATUS, member.status.name)
                .set(MEMBERS.LAST_LOGIN_AT, member.lastLoginAt?.toOffsetDateTime())
                .set(MEMBERS.VERSION, member.version)
                .set(MEMBERS.CREATED_AT, member.createdAt.toOffsetDateTime())
                .set(MEMBERS.UPDATED_AT, member.updatedAt.toOffsetDateTime())
                .execute()

            tx.deleteFrom(MEMBER_PROPERTY_ACCESSES)
                .where(MEMBER_PROPERTY_ACCESSES.MEMBER_ID.eq(member.id))
                .execute()

            member.propertyAccess.forEach { access ->
                tx.insertInto(MEMBER_PROPERTY_ACCESSES)
                    .set(MEMBER_PROPERTY_ACCESSES.MEMBER_ID, member.id)
                    .set(MEMBER_PROPERTY_ACCESSES.PROPERTY_ID, access.propertyId)
                    .set(MEMBER_PROPERTY_ACCESSES.ROLE, access.role.name)
                    .execute()
            }

            tx.findMemberById(member.id) ?: member
        }

    override fun findById(id: String): Member? =
        dsl.findMemberById(id)

    override fun findByEmail(email: String): Member? {
        val member = dsl.selectFrom(MEMBERS)
            .where(MEMBERS.EMAIL.eq(email))
            .fetchOne()
            ?: return null

        return dsl.toDomain(member)
    }

    override fun existsByEmail(email: String): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(MEMBERS)
                .where(MEMBERS.EMAIL.eq(email))
        )

    private fun DSLContext.findMemberById(id: String): Member? {
        val member = selectFrom(MEMBERS)
            .where(MEMBERS.ID.eq(id))
            .fetchOne()
            ?: return null

        return toDomain(member)
    }

    private fun DSLContext.toDomain(member: MembersRecord): Member {
        val propertyAccess = select(
            MEMBER_PROPERTY_ACCESSES.PROPERTY_ID,
            MEMBER_PROPERTY_ACCESSES.ROLE
        )
            .from(MEMBER_PROPERTY_ACCESSES)
            .where(MEMBER_PROPERTY_ACCESSES.MEMBER_ID.eq(member.get(MEMBERS.ID)))
            .orderBy(MEMBER_PROPERTY_ACCESSES.PROPERTY_ID.asc())
            .fetch { record -> record.toPropertyAccess() }

        return Member.reconstitute(
            id = member.get(MEMBERS.ID),
            email = member.get(MEMBERS.EMAIL),
            passwordHash = member.get(MEMBERS.PASSWORD_HASH),
            name = member.get(MEMBERS.NAME),
            role = MemberRole.valueOf(member.get(MEMBERS.ROLE)),
            propertyAccess = propertyAccess,
            status = MemberStatus.valueOf(member.get(MEMBERS.STATUS)),
            lastLoginAt = member.get(MEMBERS.LAST_LOGIN_AT)?.toInstant(),
            version = member.get(MEMBERS.VERSION),
            createdAt = member.get(MEMBERS.CREATED_AT).toInstant(),
            updatedAt = member.get(MEMBERS.UPDATED_AT).toInstant()
        )
    }

    private fun Record.toPropertyAccess(): PropertyAccess =
        PropertyAccess(
            propertyId = get(MEMBER_PROPERTY_ACCESSES.PROPERTY_ID),
            role = PropertyRole.valueOf(get(MEMBER_PROPERTY_ACCESSES.ROLE))
        )

    private fun Instant.toOffsetDateTime(): OffsetDateTime =
        atOffset(ZoneOffset.UTC)
}
