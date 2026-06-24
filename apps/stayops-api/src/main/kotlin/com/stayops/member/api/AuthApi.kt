package com.stayops.member.api

import com.stayops.member.api.dto.AuthResponse
import com.stayops.member.api.dto.LoginRequest
import com.stayops.member.api.dto.SignupRequest
import com.stayops.member.application.service.AuthApplication
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthApi(
    private val authApplication: AuthApplication
) {

    private val securityContextRepository = HttpSessionSecurityContextRepository()

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<AuthResponse> {
        val member = authApplication.signup(
            email = request.email,
            password = request.password,
            name = request.name
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(member, firstLogin = true))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<AuthResponse> {
        val result = authApplication.login(request.email, request.password)

        val authentication = UsernamePasswordAuthenticationToken(result.member, null, emptyList())
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)

        return ResponseEntity.ok(AuthResponse.from(result.member, firstLogin = result.firstLogin))
    }

    @PostMapping("/logout")
    fun logout(session: HttpSession): ResponseEntity<Void> {
        session.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }
}
