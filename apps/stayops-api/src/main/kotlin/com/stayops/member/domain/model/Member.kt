package com.stayops.member.domain.model

import java.time.Instant

class Member private constructor(
    val id: String,
    val email: String,
    val passwordHash: String,
    val name: String,
    val role: MemberRole,
    val propertyAccess: List<PropertyAccess>,
    val status: MemberStatus,
    val lastLoginAt: Instant?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) : java.io.Serializable {

    fun hasAccessTo(propertyId: String): Boolean {
        if (role == MemberRole.ADMIN) return true
        return propertyAccess.any { it.propertyId == propertyId }
    }

    fun grantAccess(propertyId: String, role: PropertyRole): Member {
        require(propertyAccess.none { it.propertyId == propertyId }) {
            "이미 해당 숙소에 대한 접근 권한이 있습니다: $propertyId"
        }
        return copyState(
            propertyAccess = propertyAccess + PropertyAccess(propertyId, role),
            updatedAt = Instant.now()
        )
    }

    fun revokeAccess(propertyId: String): Member {
        require(propertyAccess.any { it.propertyId == propertyId }) {
            "해당 숙소에 대한 접근 권한이 없습니다: $propertyId"
        }
        return copyState(
            propertyAccess = propertyAccess.filter { it.propertyId != propertyId },
            updatedAt = Instant.now()
        )
    }

    fun recordLogin(): Member = copyState(lastLoginAt = Instant.now(), updatedAt = Instant.now())

    fun deactivate(): Member {
        check(status == MemberStatus.ACTIVE) { "이미 비활성화된 회원입니다." }
        return copyState(status = MemberStatus.INACTIVE, updatedAt = Instant.now())
    }

    private fun copyState(
        propertyAccess: List<PropertyAccess> = this.propertyAccess,
        status: MemberStatus = this.status,
        lastLoginAt: Instant? = this.lastLoginAt,
        updatedAt: Instant = this.updatedAt
    ): Member = Member(
        id = id,
        email = email,
        passwordHash = passwordHash,
        name = name,
        role = role,
        propertyAccess = propertyAccess,
        status = status,
        lastLoginAt = lastLoginAt,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Member

        return id == other.id
    }

    override fun hashCode(): Int = 31 * javaClass.hashCode() + id.hashCode()

    override fun toString(): String = "Member(id=$id, role=$role, status=$status)"

    companion object {
        private const val serialVersionUID = 1L

        fun create(
            id: String,
            email: String,
            passwordHash: String,
            name: String,
            role: MemberRole
        ): Member {
            require(email.contains("@")) { "유효하지 않은 이메일입니다: $email" }
            require(name.isNotBlank()) { "이름은 공백일 수 없습니다." }

            val now = Instant.now()
            return Member(
                id = id,
                email = email,
                passwordHash = passwordHash,
                name = name,
                role = role,
                propertyAccess = emptyList(),
                status = MemberStatus.ACTIVE,
                lastLoginAt = null,
                version = 0L,
                createdAt = now,
                updatedAt = now
            )
        }

        fun reconstitute(
            id: String,
            email: String,
            passwordHash: String,
            name: String,
            role: MemberRole,
            propertyAccess: List<PropertyAccess>,
            status: MemberStatus,
            lastLoginAt: Instant?,
            version: Long,
            createdAt: Instant,
            updatedAt: Instant
        ): Member = Member(
            id = id,
            email = email,
            passwordHash = passwordHash,
            name = name,
            role = role,
            propertyAccess = propertyAccess,
            status = status,
            lastLoginAt = lastLoginAt,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
