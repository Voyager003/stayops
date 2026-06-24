package com.stayops.member.api.customer.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CustomerLoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String
)

data class CustomerSignupRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    @field:Size(min = 8)
    val password: String,

    @field:NotBlank
    val name: String
)
