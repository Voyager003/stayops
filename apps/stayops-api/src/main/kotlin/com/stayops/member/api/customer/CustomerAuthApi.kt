package com.stayops.member.api.customer

import com.stayops.member.api.dto.AuthResponse
import com.stayops.member.api.customer.dto.CustomerLoginRequest
import com.stayops.member.api.customer.dto.CustomerSignupRequest
import com.stayops.member.application.service.CustomerAuthApplication
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
@RequestMapping("/api/v1/customer/auth")
class CustomerAuthApi(
    private val customerAuthApplication: CustomerAuthApplication
) {

    private val securityContextRepository = HttpSessionSecurityContextRepository()

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: CustomerSignupRequest): ResponseEntity<AuthResponse> {
        val member = customerAuthApplication.signup(
            email = request.email,
            password = request.password,
            name = request.name
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(member))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: CustomerLoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse
    ): ResponseEntity<AuthResponse> {
        val member = customerAuthApplication.login(request.email, request.password)

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
