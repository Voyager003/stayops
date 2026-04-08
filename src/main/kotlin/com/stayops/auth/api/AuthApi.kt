package com.stayops.auth.api

import com.stayops.auth.api.dto.AuthResponse
import com.stayops.auth.api.dto.LoginRequest
import com.stayops.auth.api.dto.SignupRequest
import com.stayops.auth.application.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthApi(
    private val authService: AuthService
) {

    private val securityContextRepository = HttpSessionSecurityContextRepository()

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<AuthResponse> {
        val member = authService.signup(
            email = request.email,
            password = request.password,
            name = request.name
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(member))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<AuthResponse> {
        val member = authService.login(request.email, request.password)

        val authentication = UsernamePasswordAuthenticationToken(member, null, emptyList())
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)

        return ResponseEntity.ok(AuthResponse.from(member))
    }

    @PostMapping("/logout")
    fun logout(session: HttpSession): ResponseEntity<Void> {
        session.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }
}
