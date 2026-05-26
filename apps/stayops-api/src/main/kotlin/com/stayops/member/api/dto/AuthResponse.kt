package com.stayops.member.api.dto

import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus

data class AuthResponse(
    val id: String,
    val email: String,
    val name: String,
    val role: MemberRole,
    val status: MemberStatus,
    val firstLogin: Boolean = false
) {
    companion object {
        fun from(member: Member, firstLogin: Boolean = false) = AuthResponse(
            id = member.id,
            email = member.email,
            name = member.name,
            role = member.role,
            status = member.status,
            firstLogin = firstLogin
        )
    }
}
