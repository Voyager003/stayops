package com.stayops.shared.security

import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CsrfTokenResponse(
    val headerName: String,
    val parameterName: String,
    val token: String
)

@RestController
@RequestMapping("/api/v1/csrf")
class CsrfApi {

    @GetMapping
    fun get(token: CsrfToken): CsrfTokenResponse =
        CsrfTokenResponse(
            headerName = token.headerName,
            parameterName = token.parameterName,
            token = token.token
        )
}
