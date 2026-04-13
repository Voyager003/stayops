package com.stayops.member.api.customer.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class CustomerLoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String
)
