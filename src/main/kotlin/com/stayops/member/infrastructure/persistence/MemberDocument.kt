package com.stayops.member.infrastructure.persistence

import com.stayops.member.domain.model.*
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document("members")
data class MemberDocument(
    @Id val id: String,
    @Indexed(unique = true) val email: String,
    val passwordHash: String,
    val name: String,
    val role: MemberRole,
    val propertyAccess: List<PropertyAccessData>,
    val status: MemberStatus,
    val lastLoginAt: Instant?,
    @Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {

    data class PropertyAccessData(
        val propertyId: String,
        val role: PropertyRole
    )

    fun toDomain(): Member = Member.reconstitute(
        id = id,
        email = email,
        passwordHash = passwordHash,
        name = name,
        role = role,
        propertyAccess = propertyAccess.map { PropertyAccess(it.propertyId, it.role) },
        status = status,
        lastLoginAt = lastLoginAt,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(member: Member) = MemberDocument(
            id = member.id,
            email = member.email,
            passwordHash = member.passwordHash,
            name = member.name,
            role = member.role,
            propertyAccess = member.propertyAccess.map {
                PropertyAccessData(it.propertyId, it.role)
            },
            status = member.status,
            lastLoginAt = member.lastLoginAt,
            version = member.version,
            createdAt = member.createdAt,
            updatedAt = member.updatedAt
        )
    }
}
