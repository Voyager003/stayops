package com.stayops.member.api.dto

import com.stayops.member.domain.model.Member
import com.stayops.member.domain.model.MemberRole
import com.stayops.member.domain.model.MemberStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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

data class LoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String
)

data class SignupRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 8)
    val password: String,

    @field:NotBlank
    val name: String
)
